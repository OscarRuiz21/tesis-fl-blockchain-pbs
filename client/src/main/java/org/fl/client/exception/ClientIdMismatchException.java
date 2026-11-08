package org.fl.client.exception;

import java.util.Map;

/** 401 {@code client_id_mismatch}: el clientId declarado no coincide con el CN del certificado mTLS. */
public class ClientIdMismatchException extends ClientException {

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
