package org.fl.coordinator.model;

import java.util.Objects;

/**
 * Estado del protocolo según {@code queryRoundStatus}.
 *
 * @param activeRound  ronda en curso, aún sin agregar
 */
public record RoundStatus(
    RoundId activeRound,
    int totalClients,
    String aggregationName
) {
    public RoundStatus {
        Objects.requireNonNull(activeRound, "activeRound must not be null");
        Objects.requireNonNull(aggregationName, "aggregationName must not be null");
        if (totalClients < 1) {
            throw new IllegalArgumentException("totalClients must be >= 1, got " + totalClients);
        }
    }
}
