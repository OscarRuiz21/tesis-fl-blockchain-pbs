package org.fl.client.model;

import java.util.Objects;

/** Ronda activa según el chaincode; el cliente no interpreta {@code strategyName}. */
public record RoundStatus(
    RoundId activeRound,
    int totalClients,
    String strategyName
) {
    public RoundStatus {
        Objects.requireNonNull(activeRound, "activeRound must not be null");
        Objects.requireNonNull(strategyName, "strategyName must not be null");
        if (totalClients < 1) {
            throw new IllegalArgumentException("totalClients must be >= 1, got " + totalClients);
        }
    }
}
