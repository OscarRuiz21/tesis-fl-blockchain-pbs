package org.fl.client.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Respuesta de {@code submitUpdate}.
 *
 * <p>El chaincode no cuenta los updates dentro de {@code submitUpdate}: ese
 * range read entraba al read-set y provocaba PHANTOM_READ_CONFLICT con K≥10.
 * Por eso {@code isLast} siempre es false y {@code updatesReceived} vale -1;
 * se conservan en el JSON por compatibilidad. Quién agrega lo decide el
 * cliente con {@code getRoundLeaderInfo}.</p>
 *
 * <p>{@code attempts} lo fija {@code BlockchainGateway}, no el chaincode:
 * un valor mayor que 1 indica reintentos por MVCC y permite separar un
 * {@code phase2_submit_ms} inflado por reintentos del costo del chaincode.</p>
 */
public record SubmitResponse(
    boolean isLast,
    int updatesReceived,
    int expectedClients,
    SubmitTimings timings,
    int attempts
) {
    public SubmitResponse {
        Objects.requireNonNull(timings, "timings must not be null");
        // Se admite >= 0 por si el chaincode volviera a contar.
        if (updatesReceived < -1) {
            throw new IllegalArgumentException(
                "updatesReceived must be >= -1, got " + updatesReceived);
        }
        if (expectedClients < 1) {
            throw new IllegalArgumentException(
                "expectedClients must be >= 1, got " + expectedClients);
        }
        if (attempts < 1) {
            throw new IllegalArgumentException(
                "attempts must be >= 1, got " + attempts);
        }
    }

    public SubmitResponse withAttempts(int newAttempts) {
        return new SubmitResponse(isLast, updatesReceived, expectedClients, timings, newAttempts);
    }

    public record SubmitTimings(
        Duration sigModelVerify,
        Duration pbsVerify,
        Duration stateWrite,
        Duration total
    ) {
        public SubmitTimings {
            Objects.requireNonNull(sigModelVerify, "sigModelVerify must not be null");
            Objects.requireNonNull(pbsVerify, "pbsVerify must not be null");
            Objects.requireNonNull(stateWrite, "stateWrite must not be null");
            Objects.requireNonNull(total, "total must not be null");
        }
    }
}
