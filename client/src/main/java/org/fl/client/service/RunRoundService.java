package org.fl.client.service;

import org.fl.client.exception.AlreadyAggregatedException;
import org.fl.client.exception.BlockchainUnavailableException;
import org.fl.client.gateway.BlockchainGateway;
import org.fl.client.gateway.FabricErrorTranslator;
import org.fl.client.gateway.PythonTrainingGateway;
import org.fl.client.model.AggregationResult;
import org.fl.client.model.ClientId;
import org.fl.client.model.GlobalModel;
import org.fl.client.model.LeaderInfo;
import org.fl.client.model.PreparedSubmission;
import org.fl.client.model.RoundId;
import org.fl.client.model.RoundResult;
import org.fl.client.model.SubmitResponse;
import org.fl.client.model.TrainingResult;
import org.fl.client.store.FileMetricsStore;
import org.fl.client.time.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Una ronda del protocolo: Etapa 1 (credencial), entrenamiento local, Etapa 2
 * (envío) y Etapa 3 (elección de líder y agregación).
 *
 * <p>Quién dispara {@code aggregateRound} lo decide el cliente y no el
 * chaincode: contar los updates dentro de {@code submitUpdate} metía un range
 * read al read-set y causaba PHANTOM_READ_CONFLICT con K≥10. El cliente
 * sondea {@code getRoundLeaderInfo}, que no pasa por validación MVCC; el líder
 * es el {@code anon_cid} lexicográficamente menor y, si no agrega a tiempo,
 * el turno pasa al siguiente. La idempotencia de {@code aggregateRound} hace
 * seguras las invocaciones concurrentes.</p>
 *
 * <p>{@code phase2_submit_ms} y {@code phase3_duration_ms} excluyen el sondeo.
 * {@code round_wall_time_ms} lo incluye; restando {@code phase3_poll_ms} se
 * obtiene la ronda sin la espera de coordinación.</p>
 */
@Service
public class RunRoundService {

    private static final Logger log = LoggerFactory.getLogger(RunRoundService.class);

    private final AnonymityService anonymity;
    private final BlockchainGateway blockchain;
    private final FabricErrorTranslator errorTranslator;
    private final PythonTrainingGateway training;
    private final FileMetricsStore metrics;
    private final TimeSource time;

    private final long pollBaseMs;
    /** Jitter uniforme en [0, pollJitterMs) para que los K clientes no sondeen a la vez. */
    private final long pollJitterMs;
    private final int maxPolls;
    /**
     * Sondeos que se espera a cada candidato antes de pasar al siguiente. Con
     * 500 ms y 5 sondeos, el relevo llega a unos 2.5 s.
     */
    private final int patiencePolls;

    public RunRoundService(AnonymityService anonymity,
                           BlockchainGateway blockchain,
                           FabricErrorTranslator errorTranslator,
                           PythonTrainingGateway training,
                           FileMetricsStore metrics,
                           TimeSource time,
                           @Value("${fl.poll-base-ms:500}") long pollBaseMs,
                           @Value("${fl.poll-jitter-ms:250}") long pollJitterMs,
                           @Value("${fl.poll-max-attempts:120}") int maxPolls,
                           @Value("${fl.poll-leader-patience:5}") int patiencePolls) {
        this.anonymity = anonymity;
        this.blockchain = blockchain;
        this.errorTranslator = errorTranslator;
        this.training = training;
        this.metrics = metrics;
        this.time = time;
        this.pollBaseMs = pollBaseMs;
        this.pollJitterMs = pollJitterMs;
        this.maxPolls = maxPolls;
        this.patiencePolls = patiencePolls;
    }

    public RoundResult runRound(
            ClientId me, RoundId r,
            String modelName, int numParams,
            Path datasetPath, Map<String, Object> hyperparams) {
        long t0 = time.nanos();

        // Etapa 1
        PreparedSubmission prep = anonymity.prepareSubmission(me, r);
        metrics.recordPhase1Timing(r, me, prep.timings());

        // Se mide aparte para que no infle round_wall_time sin columna propia;
        // con el MLP puede tomar cientos de ms.
        long tQuery = time.nanos();
        GlobalModel base = blockchain.queryGlobalModel(r);
        Duration queryGlobalModelDuration = Duration.ofNanos(time.nanos() - tQuery);
        metrics.recordQueryGlobalModel(r, me, queryGlobalModelDuration);

        long tTrain = time.nanos();
        TrainingResult trained = training.train(modelName, numParams, base, datasetPath, me, hyperparams);
        Duration trainDuration = Duration.ofNanos(time.nanos() - tTrain);
        metrics.recordTraining(r, me, trained.trainingLoss(), trained.trainingAccuracy(),
            trained.numSamples(), trainDuration);

        // Etapa 2
        byte[] sigModel = anonymity.signUpdate(r, trained.update().bytes());

        long tSubmit = time.nanos();
        SubmitResponse submit = blockchain.submitUpdate(
            prep.anonCid(), prep.credential(), trained.update(), sigModel, r);
        Duration submitDuration = Duration.ofNanos(time.nanos() - tSubmit);
        // Los tiempos internos del chaincode (pbs_verify_ms, sig_model_verify_ms)
        // separan el costo de la PBS del ruido de red al comparar pbs y none.
        metrics.recordPhase2(r, me, submitDuration, submit.timings(), submit.attempts());

        // Etapa 3
        Phase3Outcome outcome = runPhase3LeaderElection(me, r, prep.anonCid().toUncompressedHex());

        metrics.recordPhase3Trigger(r, me, outcome.triggered, outcome.aggDuration, outcome.chaincodeTimings);
        metrics.recordPhase3Polling(r, me, outcome.pollCount, outcome.pollDuration,
            outcome.wasPrimaryLeader, outcome.leaderPosition);

        Duration totalRound = Duration.ofNanos(time.nanos() - t0);
        metrics.recordRoundTotal(r, me, totalRound);

        return new RoundResult(
            r, me, trained, prep.timings(),
            submitDuration, outcome.triggered, outcome.aggDuration, outcome.chaincodeTimings, totalRound);
    }

    private Phase3Outcome runPhase3LeaderElection(ClientId me, RoundId r, String myAnonCidHex) {
        boolean triggered = false;
        Duration aggDuration = Duration.ZERO;
        AggregationResult.ChaincodeTimings chaincodeTimings = null;

        int pollCount = 0;
        Duration pollDuration = Duration.ZERO;
        boolean wasPrimaryLeader = false;
        int leaderPosition = -1;

        for (int attempt = 0; attempt < maxPolls; attempt++) {
            long tPoll = time.nanos();
            LeaderInfo info;
            try {
                info = blockchain.queryLeaderInfo(r);
            } catch (BlockchainUnavailableException e) {
                // Una consulta fallida es transitoria; no aborta la ronda.
                log.warn("[client={}] round={} poll attempt {}/{} failed: {}",
                    me.value(), r.value(), attempt + 1, maxPolls, e.getMessage());
                pollDuration = pollDuration.plus(Duration.ofNanos(time.nanos() - tPoll));
                pollCount++; // los fallos cuentan para que pollDuration/pollCount siga siendo el RTT medio
                sleepWithJitter();
                continue;
            }
            pollDuration = pollDuration.plus(Duration.ofNanos(time.nanos() - tPoll));
            pollCount++;

            if (info.modelAggregated()) {
                break;
            }

            if (!info.isRoundReady()) {
                sleepWithJitter();
                continue;
            }

            // El candidato avanza una posición cada patiencePolls sondeos.
            leaderPosition = info.positionOf(myAnonCidHex);
            int effectiveLeaderIdx = Math.min(attempt / Math.max(patiencePolls, 1),
                info.anonCidsSorted().size() - 1);
            String effectiveLeader = info.leaderAt(effectiveLeaderIdx);

            if (effectiveLeaderIdx == 0 && myAnonCidHex.equals(effectiveLeader)) {
                wasPrimaryLeader = true;
            }

            if (!myAnonCidHex.equals(effectiveLeader)) {
                sleepWithJitter();
                continue;
            }

            log.info("[client={}] round={} elected as leader (position={}, attempt={}); triggering aggregation",
                me.value(), r.value(), effectiveLeaderIdx, attempt);

            long tAgg = time.nanos();
            try {
                AggregationResult agg = blockchain.tryAggregateRound(r);
                aggDuration = Duration.ofNanos(time.nanos() - tAgg);
                chaincodeTimings = agg.timings();
                triggered = true;
                break;
            } catch (AlreadyAggregatedException e) {
                aggDuration = Duration.ofNanos(time.nanos() - tAgg);
                log.info("[client={}] round={} aggregate already done by another caller",
                    me.value(), r.value());
                break;
            } catch (BlockchainUnavailableException e) {
                aggDuration = Duration.ofNanos(time.nanos() - tAgg);
                if (errorTranslator.isMvccReadConflict(e)) {
                    // Otro candidato ganó; el siguiente sondeo verá el modelo.
                    log.warn("[client={}] round={} aggregate lost MVCC race, continuing poll",
                        me.value(), r.value());
                    sleepWithJitter();
                    continue;
                }
                throw e;
            }
        }

        return new Phase3Outcome(
            triggered, aggDuration, chaincodeTimings,
            pollCount, pollDuration, wasPrimaryLeader, leaderPosition);
    }

    private void sleepWithJitter() {
        try {
            long sleepMs = pollBaseMs;
            if (pollJitterMs > 0) {
                sleepMs += ThreadLocalRandom.current().nextLong(pollJitterMs);
            }
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Phase3Outcome(
        boolean triggered,
        Duration aggDuration,
        AggregationResult.ChaincodeTimings chaincodeTimings,
        int pollCount,
        Duration pollDuration,
        boolean wasPrimaryLeader,
        int leaderPosition
    ) { }
}
