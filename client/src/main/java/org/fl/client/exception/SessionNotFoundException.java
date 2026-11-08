package org.fl.client.exception;

import org.fl.client.model.SessionId;

import java.util.Map;

/** 404 del AS en {@code POST /sign}: sesión inexistente, expirada o ya consumida. */
public class SessionNotFoundException extends ClientException {

    private final SessionId sessionId;

    public SessionNotFoundException(SessionId sessionId) {
        super(
            "Session not found or expired: %s".formatted(sessionId.asString()),
            Map.of("sessionId", sessionId)
        );
        this.sessionId = sessionId;
    }

    public SessionId getSessionId() { return sessionId; }
}
