package org.fl.client.exception;

import java.util.Map;

/** Falla irrecuperable de una ronda; deja al cliente en {@code ERROR}. */
public class ClientRoundFailedException extends ClientException {

    public ClientRoundFailedException(String message) {
        super(message, Map.of());
    }

    public ClientRoundFailedException(String message, Throwable cause) {
        super(message, Map.of(), cause);
    }
}
