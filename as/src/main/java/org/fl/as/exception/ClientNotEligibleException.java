package org.fl.as.exception;

import org.fl.as.model.ClientId;

import java.util.Map;

/** El cliente no está en la whitelist del experimento. */
public class ClientNotEligibleException extends AuthException {

    private final ClientId clientId;

    public ClientNotEligibleException(ClientId clientId) {
        super(
            "Client %s is not eligible to participate".formatted(clientId.value()),
            Map.of("clientId", clientId)
        );
        this.clientId = clientId;
    }

    public ClientId getClientId() { return clientId; }
}
