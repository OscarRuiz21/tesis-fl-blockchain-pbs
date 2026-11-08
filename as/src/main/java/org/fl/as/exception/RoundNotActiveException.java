package org.fl.as.exception;

import org.fl.as.model.RoundId;

import java.util.Map;

/** Credencial pedida para una ronda que ya concluyó ({@code roundId < activeRound}). */
public class RoundNotActiveException extends AuthException {

    private final RoundId requestedRound;
    private final long activeRound;

    public RoundNotActiveException(RoundId requestedRound, long activeRound) {
        super(
            "Round %d has already concluded (active round is %d)"
                .formatted(requestedRound.value(), activeRound),
            Map.of("requestedRound", requestedRound, "activeRound", activeRound)
        );
        this.requestedRound = requestedRound;
        this.activeRound = activeRound;
    }

    public RoundId getRequestedRound() { return requestedRound; }

    public long getActiveRound() { return activeRound; }
}
