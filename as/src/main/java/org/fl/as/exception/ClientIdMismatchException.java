package org.fl.as.exception;

import java.util.Map;

/**
 * El {@code clientId} declarado no coincide con el CN del certificado mTLS.
 * Sin esta validación, client-2 podría presentar su propio certificado y
 * declarar {@code clientId="client-1"} para obtener una credencial ajena.
 */
public class ClientIdMismatchException extends AuthException {

    private final String declaredClientId;
    private final String certCn;

    public ClientIdMismatchException(String declaredClientId, String certCn) {
        super(
            "Cert CN '%s' does not match declared clientId '%s'"
                .formatted(certCn, declaredClientId),
            Map.of("declaredClientId", declaredClientId, "certCn", certCn)
        );
        this.declaredClientId = declaredClientId;
        this.certCn = certCn;
    }

    public String getDeclaredClientId() { return declaredClientId; }
    public String getCertCn() { return certCn; }
}
