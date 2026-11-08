package org.fl.client.model;

import java.util.Objects;

/**
 * Lo que la Etapa 1 entrega a la Etapa 2: {@code anon_cid} (que también es el
 * nulificador), la credencial y los tiempos medidos.
 */
public record PreparedSubmission(
    AnonId anonCid,
    Credential credential,
    PhaseTimings timings
) {
    public PreparedSubmission {
        Objects.requireNonNull(anonCid, "anonCid must not be null");
        Objects.requireNonNull(credential, "credential must not be null");
        Objects.requireNonNull(timings, "timings must not be null");
    }
}
