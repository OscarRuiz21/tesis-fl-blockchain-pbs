package org.fl.as.service;

import org.fl.as.exception.ClientIdMismatchException;
import org.springframework.stereotype.Service;

/**
 * Exige que el {@code clientId} declarado {@code "client-N"} venga con un
 * certificado de CN {@code "UserN@org2.fl.network"} (formato de cryptogen
 * para Org2). Cualquier otro caso se trata como suplantación.
 */
@Service
public class VerifyClientIdentityService {

    public void verify(String certCn, String declaredClientId) {
        String expectedCn = expectedCnFor(declaredClientId);
        if (!expectedCn.equals(certCn)) {
            throw new ClientIdMismatchException(declaredClientId, certCn);
        }
    }

    /** Un clientId mal formado se rechaza como mismatch, sin detallar el motivo. */
    private static String expectedCnFor(String clientId) {
        if (clientId == null || !clientId.startsWith("client-")) {
            throw new ClientIdMismatchException(
                String.valueOf(clientId), "<unparseable-clientId>");
        }
        String suffix = clientId.substring("client-".length());
        return "User" + suffix + "@org2.fl.network";
    }
}
