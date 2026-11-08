package org.fl.as.model;

/**
 * Identidad real del cliente, la que el AS conoce al emitir la credencial y
 * que nunca entra en ella.
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
