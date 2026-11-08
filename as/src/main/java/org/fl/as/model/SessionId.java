package org.fl.as.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Handle opaco de una sesión de firma. El cliente lo devuelve en {@code /sign}
 * para que el AS recupere sus nonces sin que viajen por la red.
 */
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "SessionId value must not be null");
    }

    public static SessionId random() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId of(String value) {
        return new SessionId(UUID.fromString(value));
    }

    public String asString() {
        return value.toString();
    }
}
