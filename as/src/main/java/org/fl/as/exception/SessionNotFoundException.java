package org.fl.as.exception;

import org.fl.as.model.SessionId;

import java.util.Map;

/**
 * El {@code sessionId} no existe, ya expiró o ya se consumió; la sesión se
 * descarta al firmar para que los nonces se usen una sola vez. Expirada e
 * inexistente se reportan igual.
 */
public class SessionNotFoundException extends AuthException {

    private final SessionId sessionId;

    public SessionNotFoundException(SessionId sessionId) {
        super(
            "Session not found: %s".formatted(sessionId.value()),
            Map.of("sessionId", sessionId)
        );
        this.sessionId = sessionId;
    }

    public SessionId getSessionId() { return sessionId; }
}
