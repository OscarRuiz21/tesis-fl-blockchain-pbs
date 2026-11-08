package org.fl.coordinator.crypto;

import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Codificación Base64 de puntos y escalares P-256, copiada del chaincode para
 * que los bytes coincidan. El coordinator solo la usa para llevar {@code X_AS}
 * del AS a {@code initProtocol}.
 *
 * <p>Puntos sin comprimir ({@code 0x04 ‖ x ‖ y}, 65 bytes); escalares de
 * 32 bytes big-endian.</p>
 */
public final class CryptoCodec {

    static final int SCALAR_BYTE_LENGTH = 32;

    private CryptoCodec() {}

    /** Rechaza el punto al infinito y los puntos fuera de la curva. */
    public static ECPoint decodePoint(String base64, ECCurve curve) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        ECPoint point = curve.decodePoint(bytes);
        if (point.isInfinity()) {
            throw new IllegalArgumentException("Point at infinity is not allowed");
        }
        if (!point.isValid()) {
            throw new IllegalArgumentException("Point is not on the curve");
        }
        return point.normalize();
    }

    public static String encodePoint(ECPoint point) {
        byte[] encoded = point.normalize().getEncoded(false);
        return Base64.getEncoder().encodeToString(encoded);
    }

    /** Rechaza escalares fuera de {@code [1, n-1]}. */
    public static BigInteger decodeScalar(String base64, BigInteger n) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        BigInteger scalar = new BigInteger(1, bytes);
        if (scalar.signum() <= 0 || scalar.compareTo(n) >= 0) {
            throw new IllegalArgumentException(
                    "Scalar out of valid range [1, n-1]");
        }
        return scalar;
    }

    public static String encodeScalar(BigInteger scalar) {
        byte[] bytes = scalarToBytes32(scalar);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Exactamente 32 bytes big-endian, con ceros a la izquierda. */
    public static byte[] scalarToBytes32(BigInteger scalar) {
        byte[] raw = scalar.toByteArray();
        byte[] result = new byte[SCALAR_BYTE_LENGTH];
        if (raw.length <= SCALAR_BYTE_LENGTH) {
            System.arraycopy(raw, 0, result, SCALAR_BYTE_LENGTH - raw.length, raw.length);
        } else {
            // Descarta el byte de signo 0x00 que BigInteger antepone a los positivos.
            System.arraycopy(raw, raw.length - SCALAR_BYTE_LENGTH, result, 0, SCALAR_BYTE_LENGTH);
        }
        return result;
    }

    /** Escalar uniforme en {@code [1, n-1]} por rechazo. */
    public static BigInteger randomScalar(BigInteger n, SecureRandom random) {
        BigInteger scalar;
        do {
            scalar = new BigInteger(n.bitLength(), random);
        } while (scalar.signum() <= 0 || scalar.compareTo(n) >= 0);
        return scalar;
    }
}
