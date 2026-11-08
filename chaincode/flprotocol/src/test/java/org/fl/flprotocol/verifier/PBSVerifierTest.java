package org.fl.flprotocol.verifier;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.flprotocol.crypto.CryptoCodec;
import org.fl.flprotocol.crypto.HashToCurve;
import org.fl.flprotocol.crypto.PBSDomainParams;
import org.fl.flprotocol.crypto.ProtocolHash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de {@link PBSVerifier} sin Fabric: cada uno parte de una credencial
 * válida y altera un componente para comprobar que {@code verify} devuelve
 * {@code false}.
 */
class PBSVerifierTest {

    private static PBSDomainParams params;
    private static SecureRandom random;
    private static PBSVerifier verifier;

    @BeforeAll
    static void setupAll() {
        params = PBSDomainParams.p256();
        random = new SecureRandom();
        verifier = new PBSVerifier(params);
    }

    @Test
    void shouldVerifyValidCredential() {
        var v = AbeOkamotoTestVector.valid(params, random, 5L);

        assertTrue(verifier.verify(
            v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            v.anonCidB64(), 5L, v.xasB64()));
    }

    @Test
    void shouldRejectTamperedRho() {
        var v = AbeOkamotoTestVector.valid(params, random, 1L);
        String tampered = bumpScalarB64(v.rhoB64(), params);

        assertFalse(verifier.verify(
            tampered, v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            v.anonCidB64(), 1L, v.xasB64()));
    }

    @Test
    void shouldRejectTamperedOmega() {
        var v = AbeOkamotoTestVector.valid(params, random, 1L);
        String tampered = bumpScalarB64(v.omegaB64(), params);

        assertFalse(verifier.verify(
            v.rhoB64(), tampered, v.sigmaB64(), v.deltaB64(),
            v.anonCidB64(), 1L, v.xasB64()));
    }

    @Test
    void shouldRejectTamperedSigma() {
        var v = AbeOkamotoTestVector.valid(params, random, 1L);
        String tampered = bumpScalarB64(v.sigmaB64(), params);

        assertFalse(verifier.verify(
            v.rhoB64(), v.omegaB64(), tampered, v.deltaB64(),
            v.anonCidB64(), 1L, v.xasB64()));
    }

    @Test
    void shouldRejectTamperedDelta() {
        var v = AbeOkamotoTestVector.valid(params, random, 1L);
        String tampered = bumpScalarB64(v.deltaB64(), params);

        assertFalse(verifier.verify(
            v.rhoB64(), v.omegaB64(), v.sigmaB64(), tampered,
            v.anonCidB64(), 1L, v.xasB64()));
    }

    @Test
    void shouldRejectWrongRound() {
        // Credencial de la ronda 1 presentada en la ronda 2: otro Z_r.
        var v = AbeOkamotoTestVector.valid(params, random, 1L);

        assertFalse(verifier.verify(
            v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            v.anonCidB64(), 2L, v.xasB64()));
    }

    @Test
    void shouldRejectWrongAnonCid() {
        var v = AbeOkamotoTestVector.valid(params, random, 1L);
        ECPoint other = params.G().multiply(BigInteger.valueOf(99999)).normalize();
        String otherCidB64 = CryptoCodec.encodePoint(other);

        assertFalse(verifier.verify(
            v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            otherCidB64, 1L, v.xasB64()));
    }

    @Test
    void shouldRejectDifferentASKey() {
        var v = AbeOkamotoTestVector.valid(params, random, 1L);
        BigInteger otherXas = CryptoCodec.randomScalar(params.n(), random);
        ECPoint otherXasPoint = params.G().multiply(otherXas).normalize();
        String otherXasB64 = CryptoCodec.encodePoint(otherXasPoint);

        assertFalse(verifier.verify(
            v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            v.anonCidB64(), 1L, otherXasB64));
    }

    @Test
    void shouldReturnFalseOnMalformedBase64() {
        // Base64 inválido en cada parámetro: false, sin excepción.
        var v = AbeOkamotoTestVector.valid(params, random, 1L);

        assertFalse(verifier.verify("***", v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            v.anonCidB64(), 1L, v.xasB64()));
        assertFalse(verifier.verify(v.rhoB64(), "***", v.sigmaB64(), v.deltaB64(),
            v.anonCidB64(), 1L, v.xasB64()));
        assertFalse(verifier.verify(v.rhoB64(), v.omegaB64(), "***", v.deltaB64(),
            v.anonCidB64(), 1L, v.xasB64()));
        assertFalse(verifier.verify(v.rhoB64(), v.omegaB64(), v.sigmaB64(), "***",
            v.anonCidB64(), 1L, v.xasB64()));
        assertFalse(verifier.verify(v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            "***", 1L, v.xasB64()));
        assertFalse(verifier.verify(v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            v.anonCidB64(), 1L, "***"));
    }

    @Test
    void shouldRejectPointAtInfinityAnonCid() {
        // Prefijo 0x04 con coordenadas en cero: no es un punto de la curva.
        byte[] infinityBytes = new byte[65];
        infinityBytes[0] = 0x04;
        String infinityB64 = Base64.getEncoder().encodeToString(infinityBytes);

        var v = AbeOkamotoTestVector.valid(params, random, 1L);

        assertFalse(verifier.verify(
            v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            infinityB64, 1L, v.xasB64()));
    }

    @Test
    void shouldRejectZeroScalar() {
        byte[] zeroBytes = new byte[32];
        String zeroB64 = Base64.getEncoder().encodeToString(zeroBytes);

        var v = AbeOkamotoTestVector.valid(params, random, 1L);

        // ρ = 0 está fuera de [1, n-1].
        assertFalse(verifier.verify(
            zeroB64, v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            v.anonCidB64(), 1L, v.xasB64()));
    }

    /** Suma 1 (mod n): altera el escalar sin sacarlo del rango válido. */
    private static String bumpScalarB64(String b64, PBSDomainParams params) {
        BigInteger v = CryptoCodec.decodeScalar(b64, params.n());
        BigInteger bumped = v.add(BigInteger.ONE).mod(params.n());
        if (bumped.signum() == 0) bumped = BigInteger.ONE;
        return CryptoCodec.encodeScalar(bumped);
    }

    /** Credencial válida obtenida al recorrer la emisión completa entre AS y cliente. */
    record AbeOkamotoTestVector(
            String rhoB64, String omegaB64, String sigmaB64, String deltaB64,
            String anonCidB64, String xasB64) {

        static AbeOkamotoTestVector valid(PBSDomainParams params, SecureRandom random, long roundId) {
            // Claves del AS
            BigInteger xAS = CryptoCodec.randomScalar(params.n(), random);
            ECPoint XAS = params.G().multiply(xAS).normalize();

            // Identidad anónima del cliente
            BigInteger anonSk = CryptoCodec.randomScalar(params.n(), random);
            ECPoint anonCid = params.G().multiply(anonSk).normalize();

            ECPoint Zr = HashToCurve.compute(roundId, params);

            // Compromisos del AS
            BigInteger u = CryptoCodec.randomScalar(params.n(), random);
            BigInteger sAux = CryptoCodec.randomScalar(params.n(), random);
            BigInteger dAux = CryptoCodec.randomScalar(params.n(), random);
            ECPoint A = params.G().multiply(u).normalize();
            ECPoint B = params.G().multiply(sAux).add(Zr.multiply(dAux)).normalize();

            // El cliente cega
            BigInteger t1 = CryptoCodec.randomScalar(params.n(), random);
            BigInteger t2 = CryptoCodec.randomScalar(params.n(), random);
            BigInteger t3 = CryptoCodec.randomScalar(params.n(), random);
            BigInteger t4 = CryptoCodec.randomScalar(params.n(), random);

            ECPoint alpha = A.add(params.G().multiply(t1)).add(XAS.multiply(t2)).normalize();
            ECPoint beta  = B.add(params.G().multiply(t3)).add(Zr.multiply(t4)).normalize();
            BigInteger eps = ProtocolHash.computeAbeOkamotoChallenge(alpha, beta, Zr, anonCid);
            BigInteger e = eps.subtract(t2).subtract(t4).mod(params.n());

            // Respuesta del AS
            BigInteger c = e.subtract(dAux).mod(params.n());
            BigInteger rAns = u.subtract(c.multiply(xAS)).mod(params.n());

            // El cliente desciega
            BigInteger rho   = rAns.add(t1).mod(params.n());
            BigInteger omega = c.add(t2).mod(params.n());
            BigInteger sigma = sAux.add(t3).mod(params.n());
            BigInteger delta = dAux.add(t4).mod(params.n());

            return new AbeOkamotoTestVector(
                CryptoCodec.encodeScalar(rho),
                CryptoCodec.encodeScalar(omega),
                CryptoCodec.encodeScalar(sigma),
                CryptoCodec.encodeScalar(delta),
                CryptoCodec.encodePoint(anonCid),
                CryptoCodec.encodePoint(XAS));
        }
    }
}
