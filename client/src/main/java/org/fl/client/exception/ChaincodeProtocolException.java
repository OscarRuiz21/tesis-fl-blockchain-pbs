package org.fl.client.exception;

import java.util.Map;

/** Error del chaincode sin excepción propia, p. ej. {@code AGGREGATION_NOT_READY}. */
public class ChaincodeProtocolException extends ClientException {

    private final String chaincodeMessage;

    public ChaincodeProtocolException(String chaincodeMessage) {
        super(
            "Chaincode error: %s".formatted(chaincodeMessage),
            Map.of("chaincodeMessage", chaincodeMessage == null ? "" : chaincodeMessage)
        );
        this.chaincodeMessage = chaincodeMessage;
    }

    public String getChaincodeMessage() { return chaincodeMessage; }
}
