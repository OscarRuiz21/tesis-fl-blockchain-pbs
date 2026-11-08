package org.fl.flprotocol.crypto;

import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

/** Parámetros de dominio de NIST P-256 (secp256r1, 128 bits de seguridad). */
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
