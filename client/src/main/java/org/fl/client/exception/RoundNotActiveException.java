package org.fl.client.exception;

import org.fl.client.model.RoundId;

import java.util.Map;

/** Solicitud al AS o al chaincode para una ronda que no es la activa. */
public class RoundNotActiveException extends ClientException {

    private final RoundId requested;
    private final RoundId active;

    public RoundNotActiveException(RoundId requested, RoundId active) {
        super(
            "Round %d is not active (active=%d)".formatted(requested.value(), active.value()),
            Map.of("requested", requested, "active", active)
        );
        this.requested = requested;
        this.active = active;
    }

    public RoundId getRequested() { return requested; }
    public RoundId getActive() { return active; }
}
