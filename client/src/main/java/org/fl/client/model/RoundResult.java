package org.fl.client.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Métricas de una ronda de un cliente, una fila del CSV. {@code chaincodeTimings}
 * es null si este cliente no disparó la agregación.
 */
public record RoundResult(
    RoundId roundId,
    ClientId clientId,
    TrainingResult training,
    PhaseTimings phase1Timings,
    Duration phase2SubmitDuration,
    boolean aggregationTriggered,
    Duration phase3Duration,
    AggregationResult.ChaincodeTimings chaincodeTimings,
    Duration roundWallTime
) {
    public RoundResult {
        Objects.requireNonNull(roundId, "roundId must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(training, "training must not be null");
        Objects.requireNonNull(phase1Timings, "phase1Timings must not be null");
        Objects.requireNonNull(phase2SubmitDuration, "phase2SubmitDuration must not be null");
        Objects.requireNonNull(phase3Duration, "phase3Duration must not be null");
        Objects.requireNonNull(roundWallTime, "roundWallTime must not be null");
    }
}
