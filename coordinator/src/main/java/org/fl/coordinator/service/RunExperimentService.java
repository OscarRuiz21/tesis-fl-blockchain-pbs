package org.fl.coordinator.service;

import org.fl.coordinator.exception.ExperimentAbortedException;
import org.fl.coordinator.gateway.BlockchainGateway;
import org.fl.coordinator.gateway.ClientControlGateway;
import org.fl.coordinator.gateway.PythonEvalGateway;
import org.fl.coordinator.gateway.PythonEvalGateway.EvalParams;
import org.fl.coordinator.model.ClientId;
import org.fl.coordinator.model.ClientStatus;
import org.fl.coordinator.model.EvaluationResult;
import org.fl.coordinator.model.ExperimentRun;
import org.fl.coordinator.model.ExperimentSpec;
import org.fl.coordinator.model.GlobalEvalRecord;
import org.fl.coordinator.model.GlobalModel;
import org.fl.coordinator.model.RoundId;
import org.fl.coordinator.model.RoundStatus;
import org.fl.coordinator.store.GlobalEvalCsvWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Ciclo de vida de un experimento: inicializa contrato y AS, espera a que los
 * K clientes estén IDLE, les manda el start y, mientras corren, evalúa el
 * modelo global cada vez que {@code queryRoundStatus} muestra que avanzó la
 * ronda. Si algún cliente no llega al estado esperado a tiempo, el
 * experimento se aborta: no se continúa con menos de K.
 */
@Service
public class RunExperimentService {

    private static final Logger log = LoggerFactory.getLogger(RunExperimentService.class);

    private final BootstrapProtocolService bootstrap;
    private final ClientControlGateway clientControl;
    private final BlockchainGateway blockchain;
    private final PythonEvalGateway evalGateway;
    private final GlobalEvalCsvWriter evalWriter;
    private final Clock clock;
    private final Duration pollingInterval;
    private final Duration globalTimeout;
    private final Duration clientReadyTimeout;

    public RunExperimentService(
            BootstrapProtocolService bootstrap,
            ClientControlGateway clientControl,
            BlockchainGateway blockchain,
            PythonEvalGateway evalGateway,
            GlobalEvalCsvWriter evalWriter,
            Clock clock,
            @Value("${fl.polling-interval-ms}") long pollingIntervalMs,
            @Value("${fl.global-timeout-ms}") long globalTimeoutMs,
            @Value("${fl.client-ready-timeout-ms}") long clientReadyTimeoutMs) {
        this.bootstrap = bootstrap;
        this.clientControl = clientControl;
        this.blockchain = blockchain;
        this.evalGateway = evalGateway;
        this.evalWriter = evalWriter;
        this.clock = clock;
        this.pollingInterval = Duration.ofMillis(pollingIntervalMs);
        this.globalTimeout = Duration.ofMillis(globalTimeoutMs);
        this.clientReadyTimeout = Duration.ofMillis(clientReadyTimeoutMs);
    }

    public ExperimentRun run(ExperimentSpec spec) {
        Instant startedAt = clock.instant();

        bootstrap.bootstrap(spec);

        List<ClientId> clientIds = IntStream.rangeClosed(1, spec.k())
            .mapToObj(i -> ClientId.of("client-" + i))
            .toList();

        waitForState(clientIds, ClientStatus.IDLE, clientReadyTimeout);
        log.info("All {} clients are IDLE, sending start commands", spec.k());

        clientIds.forEach(id -> clientControl.startExperiment(id, spec));

        // El coordinator solo observa. La agregación la dispara el líder que
        // eligen los clientes (el menor anonCidHex, vía getRoundLeaderInfo);
        // invocar aggregateRound desde aquí rompería esa atribución y mezclaría
        // agregaciones concurrentes en los tiempos del chaincode.
        List<GlobalEvalRecord> evals = new ArrayList<>();
        long currentRound = 1;
        while (currentRound <= spec.totalRounds()) {
            checkGlobalTimeout(startedAt);

            RoundStatus status = blockchain.queryRoundStatus();
            if (status.activeRound().value() > currentRound) {
                GlobalEvalRecord record = evaluateRound(spec, RoundId.of(currentRound));
                evals.add(record);
                evalWriter.append(spec.label(), record);
                log.info("Round {} eval: accuracy={} loss={}",
                    currentRound, record.accuracy(), record.loss());
                currentRound++;
            } else {
                sleep(pollingInterval);
            }
        }

        waitForState(clientIds, ClientStatus.DONE, clientReadyTimeout);

        Instant finishedAt = clock.instant();
        return new ExperimentRun(
            spec, startedAt, finishedAt, evals,
            spec.k(),  // si no hubo aborto, terminaron los K
            0);
    }

    private GlobalEvalRecord evaluateRound(ExperimentSpec spec, RoundId roundId) {
        // El agregado de la ronda R se guarda bajo R+1: el chaincode lo promueve
        // a modelo global al avanzar el contador.
        GlobalModel model = blockchain.queryGlobalModel(roundId.next());
        EvalParams params = new EvalParams(
            spec.modelName(),
            spec.numParams(),
            spec.testSetPath(),
            spec.hyperparams());
        EvaluationResult eval = evalGateway.evaluate(model, params);
        return new GlobalEvalRecord(
            roundId, eval.accuracy(), eval.loss(),
            eval.evaluationTime(), clock.instant());
    }

    private void waitForState(List<ClientId> clients, ClientStatus expected, Duration timeout) {
        Instant deadline = clock.instant().plus(timeout);
        while (clock.instant().isBefore(deadline)) {
            boolean allMatch = clients.stream()
                .allMatch(id -> clientControl.queryStatus(id) == expected);
            if (allMatch) {
                return;
            }
            sleep(pollingInterval);
        }
        throw new ExperimentAbortedException(
            "Timeout waiting for clients to reach state " + expected);
    }

    private void checkGlobalTimeout(Instant startedAt) {
        if (clock.instant().isAfter(startedAt.plus(globalTimeout))) {
            throw new ExperimentAbortedException(
                "Global timeout exceeded (" + globalTimeout + ")");
        }
    }

    private void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExperimentAbortedException("Interrupted", e);
        }
    }
}
