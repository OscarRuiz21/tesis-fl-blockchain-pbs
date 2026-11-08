package org.fl.client.service;

import org.fl.client.exception.ClientRoundFailedException;
import org.fl.client.gateway.BlockchainGateway;
import org.fl.client.model.ClientId;
import org.fl.client.model.ClientState;
import org.fl.client.model.ExperimentRun;
import org.fl.client.model.RoundId;
import org.fl.client.model.RoundResult;
import org.fl.client.model.RoundStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recorre las rondas del experimento. Los clientes se sincronizan solo por el
 * ledger, sin barrera externa: la agregación avanza la ronda activa y cada
 * cliente lo detecta al sondear {@code queryRoundStatus}.
 */
@Service
public class RunAllRoundsService {

    private static final Logger log = LoggerFactory.getLogger(RunAllRoundsService.class);

    private final RunRoundService runRound;
    private final BlockchainGateway blockchain;
    private final AuditService audit;
    private final Clock clock;
    private final Duration pollingInterval;
    private final Duration pollingTimeout;

    public RunAllRoundsService(RunRoundService runRound,
                               BlockchainGateway blockchain,
                               AuditService audit,
                               Clock clock,
                               @Value("${fl.polling-interval-ms}") long pollingIntervalMs,
                               @Value("${fl.polling-timeout-ms}") long pollingTimeoutMs) {
        this.runRound = runRound;
        this.blockchain = blockchain;
        this.audit = audit;
        this.clock = clock;
        this.pollingInterval = Duration.ofMillis(pollingIntervalMs);
        this.pollingTimeout = Duration.ofMillis(pollingTimeoutMs);
    }

    public ExperimentRun runAll(
            ClientId me,
            String experimentLabel,
            int totalRounds,
            String modelName,
            int numParams,
            Path datasetPath,
            Map<String, Object> hyperparams) {
        List<RoundResult> results = new ArrayList<>();

        for (long rIdx = 1; rIdx <= totalRounds; rIdx++) {
            RoundId roundId = RoundId.of(rIdx);

            waitForRound(roundId);

            log.info("[client={}] Starting round {}", me.value(), rIdx);
            RoundResult result = runRound.runRound(
                me, roundId, modelName, numParams, datasetPath, hyperparams);
            results.add(result);

            // Cuando runRound regresa, la ronda ya está agregada.
            audit.audit(roundId, me, result.training().update().bytes());

            if (rIdx < totalRounds) {
                waitForRound(RoundId.of(rIdx + 1));
            }
        }

        return new ExperimentRun(me, experimentLabel, results, ClientState.DONE);
    }

    private void waitForRound(RoundId expected) {
        Instant start = clock.instant();
        Instant deadline = start.plus(pollingTimeout);

        while (clock.instant().isBefore(deadline)) {
            RoundStatus status = blockchain.queryRoundStatus();
            if (status.activeRound().value() >= expected.value()) {
                return;
            }
            try {
                Thread.sleep(pollingInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClientRoundFailedException(
                    "Interrupted while waiting for round " + expected.value(), e);
            }
        }

        throw new ClientRoundFailedException(
            "Timeout waiting for round " + expected.value()
            + " (waited " + pollingTimeout + ")");
    }
}
