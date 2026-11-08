package org.fl.client.model;

import org.bouncycastle.math.ec.ECPoint;

import java.util.Objects;

/**
 * Compromiso dual del AS: {@code A = u · G} (Rama A, honesta) y
 * {@code B = s · G + d · Z_r} (Rama B, simulada). La Rama B se arma al revés,
 * muestreando {@code s} y {@code d} antes del punto, para simular el
 * conocimiento del logaritmo discreto de {@code Z_r} sin tenerlo.
 */
public record Commitment(ECPoint A, ECPoint B) {

    public Commitment {
        Objects.requireNonNull(A, "Commitment A must not be null");
        Objects.requireNonNull(B, "Commitment B must not be null");
        if (A.isInfinity()) {
            throw new IllegalArgumentException("Commitment A cannot be the point at infinity");
        }
        if (B.isInfinity()) {
            throw new IllegalArgumentException("Commitment B cannot be the point at infinity");
        }
        if (!A.isValid()) {
            throw new IllegalArgumentException("Commitment A is not on the curve");
        }
        if (!B.isValid()) {
            throw new IllegalArgumentException("Commitment B is not on the curve");
        }
        A = A.normalize();
        B = B.normalize();
    }
}
