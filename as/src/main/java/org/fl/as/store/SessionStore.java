package org.fl.as.store;

import org.fl.as.model.SessionId;
import org.fl.as.model.SigningSession;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Sesiones de firma en memoria; se pierden al reiniciar el AS.
 * {@code remove} de {@link ConcurrentHashMap} es atómico, así que de dos
 * {@link #takeIfPresent} concurrentes con el mismo {@code sessionId} solo uno
 * obtiene la sesión: los nonces se usan una sola vez.
 */
@Component
public class SessionStore {

    private final ConcurrentMap<SessionId, SigningSession> store = new ConcurrentHashMap<>();

    public void put(SigningSession session) {
        store.put(session.sessionId(), session);
    }

    public Optional<SigningSession> takeIfPresent(SessionId sessionId) {
        return Optional.ofNullable(store.remove(sessionId));
    }

    public int removeExpired(Instant cutoff) {
        int[] count = {0};
        store.entrySet().removeIf(entry -> {
            if (entry.getValue().createdAt().isBefore(cutoff)) {
                count[0]++;
                return true;
            }
            return false;
        });
        return count[0];
    }
}
