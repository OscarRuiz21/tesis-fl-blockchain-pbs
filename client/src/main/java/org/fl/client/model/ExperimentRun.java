package org.fl.client.model;

import java.util.List;
import java.util.Objects;

/** Rondas de un cliente y su estado final; las métricas ya se escribieron ronda a ronda. */
public record ExperimentRun(
    ClientId clientId,
    String experimentLabel,
    List<RoundResult> rounds,
    ClientState finalState
) {
    public ExperimentRun {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(experimentLabel, "experimentLabel must not be null");
        Objects.requireNonNull(rounds, "rounds must not be null");
        Objects.requireNonNull(finalState, "finalState must not be null");
        rounds = List.copyOf(rounds);
    }
}
