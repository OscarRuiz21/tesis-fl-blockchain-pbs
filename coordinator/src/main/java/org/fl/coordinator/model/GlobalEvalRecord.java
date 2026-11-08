package org.fl.coordinator.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Fila de {@code global_eval.csv}: evaluación del modelo global tras una ronda. */
public record GlobalEvalRecord(
    RoundId roundId,
    double accuracy,
    double loss,
    Duration evaluationTime,
    Instant timestamp
) {
    public GlobalEvalRecord {
        Objects.requireNonNull(roundId, "roundId must not be null");
        Objects.requireNonNull(evaluationTime, "evaluationTime must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (accuracy < 0 || accuracy > 1.0) {
            throw new IllegalArgumentException("accuracy in [0,1], got " + accuracy);
        }
        if (Double.isNaN(loss) || Double.isInfinite(loss)) {
            throw new IllegalArgumentException("loss must be finite, got " + loss);
        }
    }
}
