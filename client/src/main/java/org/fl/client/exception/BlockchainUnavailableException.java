package org.fl.client.exception;

import java.util.Map;

/** Falla de infraestructura de la red Fabric (gateway, timeout, orderer caído). */
public class BlockchainUnavailableException extends ClientException {

    public BlockchainUnavailableException(Throwable cause) {
        super(
            "Blockchain unavailable: %s".formatted(cause == null ? "<no cause>" : cause.getMessage()),
            Map.of(),
            cause
        );
    }

    public BlockchainUnavailableException(String message, Throwable cause) {
        super(message, Map.of(), cause);
    }
}
