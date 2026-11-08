package org.fl.flprotocol.crypto;

import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.StandardDSAEncoding;
import org.bouncycastle.math.ec.ECPoint;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Firma de autoría (P6): {@code σ_model = Sign(anon_sk, Δw)} con
 * ECDSA-P256-SHA256 y codificación DER {@code SEQUENCE(r, s)}.
 *
 * <p>Se usa la API ligera de Bouncy Castle en lugar de JCA para no registrar
 * un provider. En la verificación, cualquier excepción al decodificar la firma
 * o la clave se reduce a {@code false}: una entrada malformada solo provoca el
 * rechazo de la transacción.</p>
 */
public final class ModelSigner {

    private ModelSigner() {}

    /** @return firma DER, de longitud variable (70 a 72 bytes en P-256). */
    public static byte[] sign(
            BigInteger anonSk,
            byte[] message,
            PBSDomainParams params,
            SecureRandom random) {
        ECDomainParameters domain = toDomainParams(params);
        byte[] digest = sha256(message);

        ECDSASigner signer = new ECDSASigner();
        signer.init(true, new ParametersWithRandom(
            new ECPrivateKeyParameters(anonSk, domain), random));
        BigInteger[] sig = signer.generateSignature(digest);

        try {
            return StandardDSAEncoding.INSTANCE.encode(domain.getN(), sig[0], sig[1]);
        } catch (IOException e) {
            throw new IllegalStateException("DER encoding of ECDSA signature failed", e);
        }
    }

    /** @param publicKeyPoint en el protocolo, {@code anon_cid = anon_sk·G}. */
    public static boolean verify(
            ECPoint publicKeyPoint,
            byte[] message,
            byte[] sigDer,
            PBSDomainParams params) {
        try {
            ECDomainParameters domain = toDomainParams(params);
            byte[] digest = sha256(message);

            BigInteger[] sig = StandardDSAEncoding.INSTANCE.decode(domain.getN(), sigDer);

            ECDSASigner verifier = new ECDSASigner();
            verifier.init(false, new ECPublicKeyParameters(
                publicKeyPoint.normalize(), domain));
            return verifier.verifySignature(digest, sig[0], sig[1]);
        } catch (Exception e) {
            return false;
        }
    }

    private static ECDomainParameters toDomainParams(PBSDomainParams params) {
        return new ECDomainParameters(params.curve(), params.G(), params.n());
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
