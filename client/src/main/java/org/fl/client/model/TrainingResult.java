package org.fl.client.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Salida del entrenamiento local: {@code Δw} y las métricas del último epoch.
 * {@code numSamples} solo se registra; la agregación no pondera por él.
 */
public record TrainingResult(
    ModelUpdate update,
    double trainingLoss,
    double trainingAccuracy,
    int numSamples,
    Duration duration
) {
    public TrainingResult {
        Objects.requireNonNull(update, "update must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        if (numSamples < 0) {
            throw new IllegalArgumentException(
                "numSamples must be >= 0, got " + numSamples);
        }
    }
}
