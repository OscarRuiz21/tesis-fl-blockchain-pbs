package org.fl.coordinator.exception;

import java.util.Map;

/** Falla de una llamada al AS (HTTP, handshake mTLS o respuesta inválida). Se responde 502. */
public class AuthenticationFailedException extends CoordinatorException {

    public AuthenticationFailedException(String reason) {
        super("AS communication failed: " + reason, Map.of("reason", reason));
    }

    public AuthenticationFailedException(String reason, Throwable cause) {
        super("AS communication failed: " + reason, Map.of("reason", reason), cause);
    }
}
