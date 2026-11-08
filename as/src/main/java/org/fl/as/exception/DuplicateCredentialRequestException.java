package org.fl.as.exception;

import org.fl.as.model.ClientId;
import org.fl.as.model.RoundId;

import java.util.Map;

/**
 * Segunda petición de credencial del mismo cliente en la misma ronda. Es la
 * primera barrera contra Sybil; la segunda es el nulificador en el contrato.
 */
public class DuplicateCredentialRequestException extends AuthException {

    private final ClientId clientId;
    private final RoundId round;

    public DuplicateCredentialRequestException(ClientId clientId, RoundId round) {
        super(
            "Client %s already requested a credential for round %d"
                .formatted(clientId.value(), round.value()),
            Map.of("clientId", clientId, "round", round)
        );
        this.clientId = clientId;
        this.round = round;
    }

    public ClientId getClientId() { return clientId; }
    public RoundId getRound() { return round; }
}
