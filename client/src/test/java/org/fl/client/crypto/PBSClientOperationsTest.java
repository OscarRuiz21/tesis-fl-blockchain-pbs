package org.fl.client.crypto;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.client.model.AnonId;
import org.fl.client.model.AnonSecretKey;
import org.fl.client.model.BlindResult;
import org.fl.client.model.Credential;
import org.fl.client.model.MasterSecret;
import org.fl.client.model.RoundId;
import org.fl.client.model.SignatureResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class PBSClientOperationsTest {

    private static PBSDomainParams params;
    private static SecureRandom random;

    @BeforeAll
    static void setUp() {
        params = PBSDomainParams.p256();
        random = new SecureRandom();
    }

    @Test
    void deriveAnonIdIsDeterministic() {
        MasterSecret master = MasterSecret.generate(random);
        RoundId r = RoundId.of(7);

        AnonId a = PBSClientOperations.deriveAnonId(master, r, params);
        AnonId b = PBSClientOperations.deriveAnonId(master, r, params);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void deriveAnonIdProducesECPoint() {
        MasterSecret master = MasterSecret.generate(random);
        AnonId anonCid = PBSClientOperations.deriveAnonId(master, RoundId.of(1), params);

        assertThat(anonCid.point().isInfinity()).isFalse();
        assertThat(anonCid.point().isValid()).isTrue();
        assertThat(anonCid.toUncompressedBytes()).hasSize(65);
    }

    @Test
    void deriveAnonIdConsistentWithAnonSecretKey() {
        MasterSecret master = MasterSecret.generate(random);
        RoundId r = RoundId.of(5);

        AnonId anonCid = PBSClientOperations.deriveAnonId(master, r, params);
        AnonSecretKey anonSk = AnonSecretKey.derive(master, r, params);
        ECPoint expected = params.G().multiply(anonSk.value()).normalize();

        assertThat(anonCid.point()).isEqualTo(expected);
    }

    @Test
    void deriveAnonIdDifferentRoundsProduceDifferent() {
        MasterSecret master = MasterSecret.generate(random);

        AnonId r1 = PBSClientOperations.deriveAnonId(master, RoundId.of(1), params);
        AnonId r2 = PBSClientOperations.deriveAnonId(master, RoundId.of(2), params);

        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void blindProducesValidBlindResult() {
        Fixture f = setupFixture(1L);

        BlindResult blind = PBSClientOperations.blind(
            f.A, f.B, f.anonCid, f.roundId, f.XAS, params, random);

        assertThat(blind.e().signum()).isPositive();
        assertThat(blind.t1().signum()).isPositive();
        assertThat(blind.t2().signum()).isPositive();
        assertThat(blind.t3().signum()).isPositive();
        assertThat(blind.t4().signum()).isPositive();
    }

    @Test
    void unblindRoundtripsWithSign() {
        Fixture f = setupFixture(1L);

        BlindResult blind = PBSClientOperations.blind(
            f.A, f.B, f.anonCid, f.roundId, f.XAS, params, random);

        // Simulación del AS: c = e - d_aux mod n, r_ans = u - c·x_AS mod n.
        BigInteger c = blind.e().subtract(f.dAux).mod(params.n());
        BigInteger rAns = f.u.subtract(c.multiply(f.xAS)).mod(params.n());
        SignatureResponse sigResp = new SignatureResponse(rAns, c, f.sAux, f.dAux);

        Credential cred = PBSClientOperations.unblind(sigResp, blind, params.n());

        assertThat(PBSClientOperations.verifyLocally(
            cred, f.anonCid, f.roundId, f.XAS, params)).isTrue();
    }

    @Test
    void verifyLocallyRejectsTamperedRho() {
        Fixture f = setupFixture(1L);
        Credential valid = runFullRound(f);

        Credential tampered = new Credential(
            valid.rho().add(BigInteger.ONE).mod(params.n()),
            valid.omega(), valid.sigma(), valid.delta());

        assertThat(PBSClientOperations.verifyLocally(
            tampered, f.anonCid, f.roundId, f.XAS, params)).isFalse();
    }

    @Test
    void verifyLocallyRejectsTamperedOmega() {
        Fixture f = setupFixture(1L);
        Credential valid = runFullRound(f);

        Credential tampered = new Credential(
            valid.rho(),
            valid.omega().add(BigInteger.ONE).mod(params.n()),
            valid.sigma(), valid.delta());

        assertThat(PBSClientOperations.verifyLocally(
            tampered, f.anonCid, f.roundId, f.XAS, params)).isFalse();
    }

    @Test
    void verifyLocallyRejectsWrongRound() {
        Fixture f = setupFixture(1L);
        Credential valid = runFullRound(f);

        // Con la ronda 2 cambia Z_r y la verificación falla.
        assertThat(PBSClientOperations.verifyLocally(
            valid, f.anonCid, RoundId.of(2), f.XAS, params)).isFalse();
    }


    private static class Fixture {
        BigInteger xAS;
        ECPoint XAS;
        BigInteger u, sAux, dAux;
        ECPoint A, B;
        AnonId anonCid;
        RoundId roundId;
    }

    private Fixture setupFixture(long roundIdLong) {
        Fixture f = new Fixture();
        f.xAS = CryptoCodec.randomScalar(params.n(), random);
        f.XAS = params.G().multiply(f.xAS).normalize();
        f.u = CryptoCodec.randomScalar(params.n(), random);
        f.sAux = CryptoCodec.randomScalar(params.n(), random);
        f.dAux = CryptoCodec.randomScalar(params.n(), random);

        ECPoint Zr = HashToCurve.compute(roundIdLong, params);
        f.A = params.G().multiply(f.u).normalize();
        f.B = params.G().multiply(f.sAux).add(Zr.multiply(f.dAux)).normalize();

        MasterSecret master = MasterSecret.generate(random);
        f.roundId = RoundId.of(roundIdLong);
        f.anonCid = PBSClientOperations.deriveAnonId(master, f.roundId, params);
        return f;
    }

    private Credential runFullRound(Fixture f) {
        BlindResult blind = PBSClientOperations.blind(
            f.A, f.B, f.anonCid, f.roundId, f.XAS, params, random);
        BigInteger c = blind.e().subtract(f.dAux).mod(params.n());
        BigInteger rAns = f.u.subtract(c.multiply(f.xAS)).mod(params.n());
        SignatureResponse sigResp = new SignatureResponse(rAns, c, f.sAux, f.dAux);
        return PBSClientOperations.unblind(sigResp, blind, params.n());
    }
}
