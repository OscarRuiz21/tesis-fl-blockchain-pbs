package org.fl.as.exception;

import java.util.Map;

/** La petición llegó sin certificado de cliente en el handshake mTLS. */
public class ClientNotAuthenticatedException extends AuthException {

    public ClientNotAuthenticatedException(String reason) {
        super(
            "Client not authenticated: " + reason,
            Map.of("reason", reason)
        );
    }
}
