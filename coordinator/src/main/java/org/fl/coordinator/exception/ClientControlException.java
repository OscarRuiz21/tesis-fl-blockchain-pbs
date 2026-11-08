package org.fl.coordinator.exception;

import java.util.Map;

/**
 * Falla irrecuperable al controlar un fl-client. Un error de conexión durante
 * el polling de estado no llega aquí: el gateway lo trata como IDLE porque el
 * cliente puede estar arrancando.
 */
public class ClientControlException extends CoordinatorException {

    public ClientControlException(String clientId, String reason) {
        super("Client control failed for " + clientId + ": " + reason,
            Map.of("clientId", clientId, "reason", reason));
    }

    public ClientControlException(String clientId, String reason, Throwable cause) {
        super("Client control failed for " + clientId + ": " + reason,
            Map.of("clientId", clientId, "reason", reason), cause);
    }
}
