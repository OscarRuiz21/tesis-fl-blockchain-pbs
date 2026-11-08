package org.fl.client.exception;

import org.fl.client.model.ClientId;
import org.fl.client.model.RoundId;

import java.util.Map;

/** 409 del AS: ya se emitió la única credencial de este cliente en la ronda (P2). */
public class DuplicateCredentialRequestException extends ClientException {

    private final ClientId clientId;
    private final RoundId round;

    public DuplicateCredentialRequestException(ClientId clientId, RoundId round) {
        super(
            "Duplicate credential request: client=%s round=%d"
                .formatted(clientId.value(), round.value()),
            Map.of("clientId", clientId, "round", round)
        );
        this.clientId = clientId;
        this.round = round;
    }

    public ClientId getClientId() { return clientId; }
    public RoundId getRound() { return round; }
}
