package org.fl.client.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Sesión de firma que el AS abre al emitir {@code (A, B)}; con ella recupera
 * en el paso de firma sus secretos efímeros sin enviarlos por la red.
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
