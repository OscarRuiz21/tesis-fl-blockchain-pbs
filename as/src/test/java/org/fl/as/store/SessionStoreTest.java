package org.fl.as.store;

import org.fl.as.model.ClientId;
import org.fl.as.model.RoundId;
import org.fl.as.model.SessionId;
import org.fl.as.model.SigningSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreTest {

    private SessionStore store;

    @BeforeEach
    void setUp() {
        store = new SessionStore();
    }

    @Test
    void putAndTakeRetrievesSession() {
        SigningSession s = sessionAt(Instant.parse("2026-04-26T20:00:00Z"));
        store.put(s);

        Optional<SigningSession> retrieved = store.takeIfPresent(s.sessionId());

        assertThat(retrieved).isPresent().contains(s);
    }

    @Test
    void takeIsAtomicAndRemovesSession() {
        SigningSession s = sessionAt(Instant.now());
        store.put(s);

        assertThat(store.takeIfPresent(s.sessionId())).isPresent();
        assertThat(store.takeIfPresent(s.sessionId())).isEmpty();
    }

    @Test
    void takeReturnsEmptyWhenAbsent() {
        assertThat(store.takeIfPresent(SessionId.random())).isEmpty();
    }

    @Test
    void removeExpiredEvictsOnlyOld() {
        Instant tenMinutesAgo = Instant.parse("2026-04-26T19:00:00Z");
        Instant now = Instant.parse("2026-04-26T20:00:00Z");

        SigningSession old = sessionAt(tenMinutesAgo);
        SigningSession fresh = sessionAt(now);
        store.put(old);
        store.put(fresh);

        Instant cutoff = Instant.parse("2026-04-26T19:30:00Z");
        int removed = store.removeExpired(cutoff);

        assertThat(removed).isEqualTo(1);
        assertThat(store.takeIfPresent(old.sessionId())).isEmpty();
        assertThat(store.takeIfPresent(fresh.sessionId())).isPresent();
    }

    private SigningSession sessionAt(Instant createdAt) {
        return new SigningSession(
            SessionId.random(),
            BigInteger.valueOf(123),
            BigInteger.valueOf(456),
            BigInteger.valueOf(789),
            ClientId.of("client-1"),
            RoundId.of(1),
            createdAt);
    }
}
