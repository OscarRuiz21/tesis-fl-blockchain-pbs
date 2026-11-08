package org.fl.client.model;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Respuesta del AS al paso de firma: {@code r_ans = u - c·x_AS mod n} y
 * {@code c = e - d mod n} de la Rama A, y {@code s, d} de la Rama B,
 * muestreados en el compromiso. Se acepta el cero porque es válido en
 * {@code Z_n}.
 */
public record SignatureResponse(
        BigInteger rAns,
        BigInteger c,
        BigInteger s,
        BigInteger d
) {
    public SignatureResponse {
        Objects.requireNonNull(rAns, "rAns must not be null");
        Objects.requireNonNull(c, "c must not be null");
        Objects.requireNonNull(s, "s must not be null");
        Objects.requireNonNull(d, "d must not be null");
        if (rAns.signum() < 0) {
            throw new IllegalArgumentException("rAns must be non-negative");
        }
        if (c.signum() < 0) {
            throw new IllegalArgumentException("c must be non-negative");
        }
        if (s.signum() < 0) {
            throw new IllegalArgumentException("s must be non-negative");
        }
        if (d.signum() < 0) {
            throw new IllegalArgumentException("d must be non-negative");
        }
    }
}
