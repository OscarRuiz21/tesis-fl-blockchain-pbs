package org.fl.as.service;

import lombok.RequiredArgsConstructor;
import org.fl.as.store.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/** Libera las sesiones que expiraron sin consumirse. */
@Component
@RequiredArgsConstructor
public class SessionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final SessionStore sessionStore;
    private final Duration sessionTtl;
    private final Clock clock;

    @Scheduled(fixedRateString = "${auth.cleanup-interval-ms}")
    public void cleanup() {
        int removed = sessionStore.removeExpired(clock.instant().minus(sessionTtl));
        if (removed > 0) {
            log.debug("Cleaned {} expired sessions", removed);
        }
    }
}
