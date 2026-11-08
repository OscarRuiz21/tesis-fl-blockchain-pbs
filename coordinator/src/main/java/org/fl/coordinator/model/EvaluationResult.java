package org.fl.coordinator.model;

import java.time.Duration;
import java.util.Objects;

/** Exactitud y pérdida del modelo global sobre el conjunto de prueba centralizado. */
public record EvaluationResult(double accuracy, double loss, Duration evaluationTime) {

    public EvaluationResult {
        Objects.requireNonNull(evaluationTime, "evaluationTime must not be null");
        if (accuracy < 0 || accuracy > 1.0) {
            throw new IllegalArgumentException("accuracy in [0,1], got " + accuracy);
        }
        if (Double.isNaN(loss) || Double.isInfinite(loss)) {
            throw new IllegalArgumentException("loss must be finite, got " + loss);
        }
    }
}
