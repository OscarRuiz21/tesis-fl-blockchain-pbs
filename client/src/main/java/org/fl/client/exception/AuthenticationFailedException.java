package org.fl.client.exception;

import java.util.Map;

/** Falla de comunicación con el AS; los rechazos del protocolo tienen excepción propia. */
public class AuthenticationFailedException extends ClientException {

    private final String reason;

    public AuthenticationFailedException(String reason, Throwable cause) {
        super(
            "Authentication with AS failed: %s".formatted(reason),
            Map.of("reason", reason),
            cause
        );
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
