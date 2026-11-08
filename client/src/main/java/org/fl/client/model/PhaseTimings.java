package org.fl.client.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Tiempos de la Etapa 1. Separa la criptografía local (blind, unblind,
 * verificación) de los RTT contra el AS, para reportar el costo criptográfico
 * aparte del de red.
 *
 * <p>{@code fetchPubKeyRtt} mide el GET {@code /blind-sign/public-key}; como
 * {@code ASGateway} guarda la llave, vale cero después de la primera ronda.
 * Medirlo aparte evita que ese RTT infle {@code round_wall_time} sin
 * aparecer en ninguna columna.</p>
 */
public record PhaseTimings(
    Duration blindMs,
    Duration unblindMs,
    Duration verifyLocalMs,
    Duration commitRttMs,
    Duration signRttMs,
    Duration fetchPubKeyRttMs
) {
    public PhaseTimings {
        Objects.requireNonNull(blindMs, "blindMs must not be null");
        Objects.requireNonNull(unblindMs, "unblindMs must not be null");
        Objects.requireNonNull(verifyLocalMs, "verifyLocalMs must not be null");
        Objects.requireNonNull(commitRttMs, "commitRttMs must not be null");
        Objects.requireNonNull(signRttMs, "signRttMs must not be null");
        Objects.requireNonNull(fetchPubKeyRttMs, "fetchPubKeyRttMs must not be null");
    }

    public static PhaseTimings zero() {
        return new PhaseTimings(
            Duration.ZERO, Duration.ZERO, Duration.ZERO,
            Duration.ZERO, Duration.ZERO, Duration.ZERO);
    }

    public Duration cryptoOnly() {
        return blindMs.plus(unblindMs).plus(verifyLocalMs);
    }

    public Duration phase1Total() {
        return cryptoOnly().plus(commitRttMs).plus(signRttMs).plus(fetchPubKeyRttMs);
    }
}
