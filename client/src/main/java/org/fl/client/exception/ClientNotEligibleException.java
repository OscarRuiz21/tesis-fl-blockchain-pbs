package org.fl.client.exception;

import org.fl.client.model.ClientId;

import java.util.Map;

/** 401 del AS: el cliente no está en la lista de elegibles. */
public class ClientNotEligibleException extends ClientException {

    private final ClientId clientId;

    public ClientNotEligibleException(ClientId clientId) {
        super(
            "Client not eligible: %s".formatted(clientId.value()),
            Map.of("clientId", clientId)
        );
        this.clientId = clientId;
    }

    public ClientId getClientId() { return clientId; }
}
