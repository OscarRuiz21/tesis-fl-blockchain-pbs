package org.fl.coordinator.model;

import java.util.Objects;

/** Número de rondas registradas en el CSV de un cliente. */
public record ClientMetrics(String clientId, int recordedRounds) {

    public ClientMetrics {
        Objects.requireNonNull(clientId, "clientId must not be null");
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must be non-blank");
        }
        if (recordedRounds < 0) {
            throw new IllegalArgumentException("recordedRounds must be >= 0");
        }
    }
}
