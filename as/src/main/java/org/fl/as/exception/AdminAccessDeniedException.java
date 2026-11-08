package org.fl.as.exception;

import java.util.Map;

/**
 * El certificado es válido pero su CN no es {@code auth.admin-cn}: el cliente
 * está autenticado, no autorizado (403). El CN se conserva para el log.
 */
public class AdminAccessDeniedException extends AuthException {

    private final String actualCn;

    public AdminAccessDeniedException(String actualCn) {
        super(
            "cert CN not authorized for admin operation",
            Map.of("actualCn", actualCn == null ? "<null>" : actualCn)
        );
        this.actualCn = actualCn;
    }

    public String getActualCn() { return actualCn; }
}
