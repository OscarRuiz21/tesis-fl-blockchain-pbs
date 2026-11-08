package org.fl.coordinator.gateway;

import java.util.List;

/** Cuerpos HTTP del coordinator hacia el AS. */
public final class ASDtos {

    private ASDtos() {
        throw new AssertionError("utility class");
    }

    /** Mismo formato que {@code AuthDtos.AdminInitRequest} del AS. */
    public record AdminInitRequest(
        List<String> allowedClients,
        int totalRounds,
        String experimentId
    ) {}

    /** {@code X_AS = x_AS·G} en Base64, sin comprimir (65 bytes). */
    public record PublicKeyResponse(String publicKeyB64) {}
}
