package org.fl.as.model;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.as.crypto.CryptoCodec;
import org.fl.as.crypto.PBSDomainParams;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * Par de llaves del AS: {@code x_AS} secreto y {@code X_AS = x_AS·G}, con la
 * que el chaincode verifica las credenciales.
 */
public record ASKeyPair(
    BigInteger privateKey,
    ECPoint publicKey
) {
    public ASKeyPair {
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        if (privateKey.signum() <= 0) {
            throw new IllegalArgumentException("privateKey must be positive");
        }
        if (publicKey.isInfinity()) {
            throw new IllegalArgumentException("publicKey cannot be the point at infinity");
        }
    }

    public static ASKeyPair generate(PBSDomainParams params, SecureRandom random) {
        Objects.requireNonNull(params, "params must not be null");
        Objects.requireNonNull(random, "random must not be null");
        BigInteger xAS = CryptoCodec.randomScalar(params.n(), random);
        ECPoint XAS = params.G().multiply(xAS).normalize();
        return new ASKeyPair(xAS, XAS);
    }
}
