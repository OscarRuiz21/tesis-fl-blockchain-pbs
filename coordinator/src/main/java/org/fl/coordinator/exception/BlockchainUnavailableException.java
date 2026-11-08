package org.fl.coordinator.exception;

import java.util.Map;

/** Falla de la comunicación con Fabric (peer caído, endorsement o timeout). Se responde 503. */
public class BlockchainUnavailableException extends CoordinatorException {

    public BlockchainUnavailableException(Throwable cause) {
        super("Blockchain unavailable: " + cause.getMessage(),
            Map.of("causeType", cause.getClass().getSimpleName()), cause);
    }

    public BlockchainUnavailableException(String message, Throwable cause) {
        super(message,
            Map.of("causeType", cause == null ? "<null>" : cause.getClass().getSimpleName()), cause);
    }
}
