package org.fl.as.crypto;

import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hash-to-curve sobre P-256 por <i>try-and-increment</i>: {@code Z_r = H_curve(r)}.
 *
 * <p>Mapea el identificador de ronda a un punto {@code Z_r} cuyo logaritmo
 * discreto nadie conoce. De ello depende que la rama B de la prueba OR sea
 * simulable por el AS pero no falsificable por un atacante.</p>
 *
 * <p>Algoritmo (Brier et al., 2010, sec. 4):</p>
 * <pre>
 *   counter := 0
 *   loop:
 *     digest := SHA-256(DOMAIN_SEP ‖ roundId_BE8 ‖ counter_BE4)
 *     x := BigInteger(digest) mod p
 *     rhs := (x³ + a·x + b) mod p          // a = -3 en P-256
 *     y := rhs^((p+1)/4) mod p             // raíz, pues p ≡ 3 mod 4
 *     if y² mod p == rhs:                  // rhs es residuo cuadrático
 *       if y es impar: y := p - y          // signo determinista
 *       return point(x, y)
 *     counter := counter + 1
 * </pre>
 */
public final class HashToCurve {

    /** Separador de dominio; debe coincidir byte a byte con cliente y chaincode. */
    private static final byte[] DOMAIN_SEPARATOR =
        "FL_PBS_Z_R_v1".getBytes(StandardCharsets.US_ASCII);

    /** Cada intento falla con probabilidad ~1/2; 256 fallos seguidos no ocurren en la práctica. */
    private static final int MAX_ATTEMPTS = 256;

    private HashToCurve() {}

    public static ECPoint compute(long roundId, PBSDomainParams params) {
        ECCurve.Fp curve = (ECCurve.Fp) params.curve();
        BigInteger p = curve.getQ();
        BigInteger a = curve.getA().toBigInteger();
        BigInteger b = curve.getB().toBigInteger();
        BigInteger sqrtExponent = p.add(BigInteger.ONE).shiftRight(2);

        byte[] roundBytes = ByteBuffer.allocate(8).putLong(roundId).array();

        for (int counter = 0; counter < MAX_ATTEMPTS; counter++) {
            byte[] counterBytes = ByteBuffer.allocate(4).putInt(counter).array();

            MessageDigest digest = sha256();
            digest.update(DOMAIN_SEPARATOR);
            digest.update(roundBytes);
            digest.update(counterBytes);
            byte[] hash = digest.digest();

            BigInteger x = new BigInteger(1, hash).mod(p);
            BigInteger rhs = x.modPow(BigInteger.valueOf(3), p)
                .add(a.multiply(x))
                .add(b)
                .mod(p);

            BigInteger y = rhs.modPow(sqrtExponent, p);
            // Si y² != rhs, rhs no era residuo cuadrático.
            if (!y.multiply(y).mod(p).equals(rhs)) {
                continue;
            }

            // Se elige la y par, la misma que codifica el prefijo 0x02.
            if (y.testBit(0)) {
                y = p.subtract(y);
            }

            ECPoint point = curve.createPoint(x, y).normalize();
            if (point.isInfinity() || !point.isValid()) {
                continue;
            }
            return point;
        }
        throw new IllegalStateException(
            "HashToCurve failed after " + MAX_ATTEMPTS + " attempts for roundId=" + roundId);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
