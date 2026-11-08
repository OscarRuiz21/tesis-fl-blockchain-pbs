package org.fl.client.crypto;

import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hash-to-Curve sobre NIST P-256 por <i>try-and-increment</i>, que define
 * {@code Z_r = H_curve(r)}.
 *
 * <p>Nadie conoce el logaritmo discreto de {@code Z_r}. Por eso la Rama B de
 * la prueba OR de la PBS la puede simular el AS pero no la puede falsificar
 * un atacante.</p>
 *
 * <p>Algoritmo (Brier et al., 2010, sec. 4, "Efficient Indifferentiable
 * Hashing into Ordinary Elliptic Curves"):</p>
 * <pre>
 *   counter := 0
 *   loop:
 *     digest := SHA-256(DOMAIN_SEP ‖ roundId_BE8 ‖ counter_BE4)
 *     x := BigInteger(digest) mod p
 *     rhs := (x³ + a·x + b) mod p           // a = -3 en P-256
 *     y := rhs^((p+1)/4) mod p              // raíz; p ≡ 3 mod 4 en P-256
 *     if y² mod p == rhs:                   // rhs era residuo cuadrático
 *       if y es impar: y := p - y           // signo determinista
 *       return point(x, y)
 *     counter := counter + 1
 * </pre>
 */
public final class HashToCurve {

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
        // Como p ≡ 3 (mod 4), la raíz cuadrada es rhs^((p+1)/4) mod p.
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

            // Se fija la y par, la misma convención del prefijo 0x02 en
            // la compresión de puntos.
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
