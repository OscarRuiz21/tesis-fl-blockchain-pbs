package org.fl.client.model;

/**
 * Identidad real del cliente, la que conoce el AS. Aquí solo se exige que no
 * esté vacía; la elegibilidad la valida el AS.
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
