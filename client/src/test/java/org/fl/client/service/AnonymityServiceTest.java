package org.fl.client.service;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.client.crypto.CryptoCodec;
import org.fl.client.crypto.HashToCurve;
import org.fl.client.crypto.PBSDomainParams;
import org.fl.client.gateway.ASGateway;
import org.fl.client.model.ClientId;
import org.fl.client.model.Commitment;
import org.fl.client.model.MasterSecret;
import org.fl.client.model.PreparedSubmission;
import org.fl.client.model.RoundId;
import org.fl.client.model.SignatureResponse;
import org.fl.client.time.SystemNanoTimeSource;
import org.fl.client.time.TimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Modo PBS con el AS simulado y modo NONE, que no debe contactar al AS. */
class AnonymityServiceTest {

    private PBSDomainParams params;
    private SecureRandom random;
    private MasterSecret masterSecret;
    private TimeSource time;

    @BeforeEach
    void setUp() {
        params = PBSDomainParams.p256();
        random = new SecureRandom();
        masterSecret = MasterSecret.generate(random);
        time = new SystemNanoTimeSource();
    }

    @Test
    void pbsModeProducesValidCredentialAndNonEmptySignature() {
        ASGateway asGateway = mock(ASGateway.class);

        // Simula el AS: x_AS privado + X_AS público. Para cada commit
        // muestrea (u, sAux, dAux), construye (A, B) y al recibir e
        // calcula c y rAns.
        BigInteger xAS = CryptoCodec.randomScalar(params.n(), random);
        ECPoint XAS = params.G().multiply(xAS).normalize();
        when(asGateway.fetchServerPublicKey()).thenReturn(XAS);

        BigInteger u = CryptoCodec.randomScalar(params.n(), random);
        BigInteger sAux = CryptoCodec.randomScalar(params.n(), random);
        BigInteger dAux = CryptoCodec.randomScalar(params.n(), random);
        ECPoint Zr = HashToCurve.compute(1L, params);
        ECPoint A = params.G().multiply(u).normalize();
        ECPoint B = params.G().multiply(sAux).add(Zr.multiply(dAux)).normalize();

        when(asGateway.requestCommitment(any(), eq(RoundId.of(1))))
            .thenReturn(new ASGateway.CommitmentSession(
                org.fl.client.model.SessionId.random(),
                new Commitment(A, B)));

        when(asGateway.requestSignature(any(), any())).thenAnswer(inv -> {
            BigInteger e = inv.getArgument(1);
            BigInteger c = e.subtract(dAux).mod(params.n());
            BigInteger rAns = u.subtract(c.multiply(xAS)).mod(params.n());
            return new SignatureResponse(rAns, c, sAux, dAux);
        });

        AnonymityService svc = new AnonymityService(
            params, random, asGateway, masterSecret, time, "pbs");

        ClientId me = ClientId.of("client-1");
        PreparedSubmission prep = svc.prepareSubmission(me, RoundId.of(1));

        assertThat(prep.anonCid()).isNotNull();
        assertThat(prep.credential().rho().signum()).isPositive();
        assertThat(prep.credential().omega().signum()).isPositive();
        // σ_model debe ser una firma ECDSA-DER no vacía.
        byte[] sigModel = svc.signUpdate(RoundId.of(1), new byte[]{1, 2, 3, 4});
        assertThat(sigModel).isNotEmpty();
    }

    @Test
    void noneModeProducesTrivialCredentialAndEmptySignatureWithoutContactingAs() {
        ASGateway asGateway = mock(ASGateway.class);

        AnonymityService svc = new AnonymityService(
            params, random, asGateway, masterSecret, time, "none");

        ClientId me = ClientId.of("client-1");
        PreparedSubmission prep = svc.prepareSubmission(me, RoundId.of(1));

        assertThat(prep.anonCid()).isNotNull();
        assertThat(prep.credential().rho()).isEqualTo(BigInteger.ONE);
        assertThat(prep.credential().omega()).isEqualTo(BigInteger.ONE);
        assertThat(prep.credential().sigma()).isEqualTo(BigInteger.ONE);
        assertThat(prep.credential().delta()).isEqualTo(BigInteger.ONE);

        byte[] sigModel = svc.signUpdate(RoundId.of(1), new byte[]{1, 2, 3, 4});
        assertThat(sigModel).isEmpty();

        // El modo NONE NO debe hablar con el AS.
        verify(asGateway, never()).fetchServerPublicKey();
        verify(asGateway, never()).requestCommitment(any(), any());
        verify(asGateway, never()).requestSignature(any(), any());
    }

    @Test
    void applyModeNoneAfterDefaultPbsSwitchesBranch() {
        ASGateway asGateway = mock(ASGateway.class);

        AnonymityService svc = new AnonymityService(
            params, random, asGateway, masterSecret, time, "pbs");

        svc.applyMode("none");

        assertThat(svc.mode()).isEqualTo("none");
        PreparedSubmission prep = svc.prepareSubmission(ClientId.of("client-1"), RoundId.of(1));
        assertThat(prep.credential().rho()).isEqualTo(BigInteger.ONE);
        assertThat(svc.signUpdate(RoundId.of(1), new byte[]{1, 2, 3})).isEmpty();
        // El cambio de modo evita el AS aunque el valor por omisión era pbs.
        verify(asGateway, never()).requestCommitment(any(), any());
        verify(asGateway, never()).requestSignature(any(), any());
    }

    @Test
    void applyModeRejectsInvalidValue() {
        ASGateway asGateway = mock(ASGateway.class);
        AnonymityService svc = new AnonymityService(
            params, random, asGateway, masterSecret, time, "pbs");

        assertThatThrownBy(() -> svc.applyMode("garbage"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("anonymityMode");
        assertThatThrownBy(() -> svc.applyMode(null))
            .isInstanceOf(IllegalArgumentException.class);
        // Un valor rechazado no altera el modo.
        assertThat(svc.mode()).isEqualTo("pbs");
    }

    @Test
    void applyModePbsAfterNoneRestoresAsContact() {
        ASGateway asGateway = mock(ASGateway.class);
        BigInteger xAS = CryptoCodec.randomScalar(params.n(), random);
        ECPoint XAS = params.G().multiply(xAS).normalize();
        when(asGateway.fetchServerPublicKey()).thenReturn(XAS);

        BigInteger u = CryptoCodec.randomScalar(params.n(), random);
        BigInteger sAux = CryptoCodec.randomScalar(params.n(), random);
        BigInteger dAux = CryptoCodec.randomScalar(params.n(), random);
        ECPoint Zr = HashToCurve.compute(1L, params);
        ECPoint A = params.G().multiply(u).normalize();
        ECPoint B = params.G().multiply(sAux).add(Zr.multiply(dAux)).normalize();

        when(asGateway.requestCommitment(any(), eq(RoundId.of(1))))
            .thenReturn(new ASGateway.CommitmentSession(
                org.fl.client.model.SessionId.random(),
                new Commitment(A, B)));
        when(asGateway.requestSignature(any(), any())).thenAnswer(inv -> {
            BigInteger e = inv.getArgument(1);
            BigInteger c = e.subtract(dAux).mod(params.n());
            BigInteger rAns = u.subtract(c.multiply(xAS)).mod(params.n());
            return new SignatureResponse(rAns, c, sAux, dAux);
        });

        AnonymityService svc = new AnonymityService(
            params, random, asGateway, masterSecret, time, "none");
        svc.applyMode("pbs");

        svc.prepareSubmission(ClientId.of("client-1"), RoundId.of(1));

        verify(asGateway, atLeastOnce()).requestCommitment(any(), any());
        verify(asGateway, atLeastOnce()).requestSignature(any(), any());
    }

    @Test
    void constructorRejectsInvalidDefaultMode() {
        ASGateway asGateway = mock(ASGateway.class);
        assertThatThrownBy(() -> new AnonymityService(
                params, random, asGateway, masterSecret, time, "garbage"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
