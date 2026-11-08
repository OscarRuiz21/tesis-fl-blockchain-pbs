package org.fl.coordinator.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Resultado de ejecutar un {@link ExperimentSpec}: tiempos y una evaluación por ronda. */
public record ExperimentRun(
    ExperimentSpec spec,
    Instant startedAt,
    Instant finishedAt,
    List<GlobalEvalRecord> globalEvals,
    int clientsCompleted,
    int clientsFailed
) {
    public ExperimentRun {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        Objects.requireNonNull(globalEvals, "globalEvals must not be null");
        if (clientsCompleted < 0 || clientsFailed < 0) {
            throw new IllegalArgumentException("clientsCompleted and clientsFailed must be >= 0");
        }
        globalEvals = List.copyOf(globalEvals);
    }
}
