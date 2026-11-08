package org.fl.coordinator.model;

/**
 * Identidad real de un cliente FL, {@code client-<N>} con N desde 1 como en
 * {@code docker compose --scale}. Solo se usa para la whitelist del AS y las
 * URLs de control; nunca llega al contrato.
 */
public record ClientId(String value) {

    public ClientId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ClientId must be non-null and non-blank");
        }
    }

    public static ClientId of(String value) {
        return new ClientId(value);
    }
}
