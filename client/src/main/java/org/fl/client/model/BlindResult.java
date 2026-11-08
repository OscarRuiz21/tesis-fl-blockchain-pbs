package org.fl.client.model;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Resultado del cegado: el reto cegado {@code e = ε - t_2 - t_4 mod n} que
 * viaja al AS y los factores {@code t_1..t_4} para descegar. Los factores son
 * secretos efímeros de la ronda; no salen de memoria y {@link #toString()}
 * los oculta.
 */
public record BlindResult(
        BigInteger e,
        BigInteger t1,
        BigInteger t2,
        BigInteger t3,
        BigInteger t4
) {
    public BlindResult {
        Objects.requireNonNull(e, "e must not be null");
        Objects.requireNonNull(t1, "t1 must not be null");
        Objects.requireNonNull(t2, "t2 must not be null");
        Objects.requireNonNull(t3, "t3 must not be null");
        Objects.requireNonNull(t4, "t4 must not be null");
        if (e.signum() <= 0) {
            throw new IllegalArgumentException("e must be positive");
        }
        if (t1.signum() <= 0) {
            throw new IllegalArgumentException("t1 must be positive");
        }
        if (t2.signum() <= 0) {
            throw new IllegalArgumentException("t2 must be positive");
        }
        if (t3.signum() <= 0) {
            throw new IllegalArgumentException("t3 must be positive");
        }
        if (t4.signum() <= 0) {
            throw new IllegalArgumentException("t4 must be positive");
        }
    }

    @Override
    public String toString() {
        return "BlindResult(e=<scalar>, t1=<redacted>, t2=<redacted>, "
            + "t3=<redacted>, t4=<redacted>)";
    }
}
