package org.fl.as.model;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Estado secreto del AS entre {@code commit} y {@code sign}: el nonce {@code u}
 * de la rama A, con el que se calcula {@code r_ans = u - c·x_AS mod n}, y los
 * valores simulados {@code sAux}, {@code dAux} de la rama B, de los que sale
 * {@code c = e - d mod n}. La identidad real queda aquí y no entra en la
 * credencial. Se consume entera en {@code sign} o expira por TTL.
 */
public record SigningSession(
    SessionId sessionId,
    BigInteger u,
    BigInteger sAux,
    BigInteger dAux,
    ClientId clientId,
    RoundId roundId,
    Instant createdAt
) {
    public SigningSession {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(u, "u must not be null");
        Objects.requireNonNull(sAux, "sAux must not be null");
        Objects.requireNonNull(dAux, "dAux must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(roundId, "roundId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (u.signum() <= 0) {
            throw new IllegalArgumentException("u must be positive");
        }
        if (sAux.signum() <= 0) {
            throw new IllegalArgumentException("sAux must be positive");
        }
        if (dAux.signum() <= 0) {
            throw new IllegalArgumentException("dAux must be positive");
        }
    }

    public boolean isExpired(Duration ttl, Instant now) {
        return createdAt.plus(ttl).isBefore(now);
    }
}
