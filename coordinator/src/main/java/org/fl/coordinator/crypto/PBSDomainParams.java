package org.fl.coordinator.crypto;

import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

/**
 * Parámetros de NIST P-256 (secp256r1), la curva de la PBS. Copia de la clase
 * del chaincode; el coordinator solo usa {@link #curve()} para decodificar {@code X_AS}.
 */
public record PBSDomainParams(
        ECCurve curve,
        ECPoint G,
        BigInteger n
) {
    public static PBSDomainParams p256() {
        X9ECParameters ec = ECNamedCurveTable.getByName("secp256r1");
        return new PBSDomainParams(ec.getCurve(), ec.getG(), ec.getN());
    }
}
