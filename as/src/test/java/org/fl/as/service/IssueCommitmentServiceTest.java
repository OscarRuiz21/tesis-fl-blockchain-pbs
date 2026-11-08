package org.fl.as.service;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.as.crypto.CryptoCodec;
import org.fl.as.crypto.HashToCurve;
import org.fl.as.crypto.PBSDomainParams;
import org.fl.as.exception.ClientNotEligibleException;
import org.fl.as.exception.DuplicateCredentialRequestException;
import org.fl.as.exception.RoundNotActiveException;
import org.fl.as.model.ClientId;
import org.fl.as.model.CommitmentIssued;
import org.fl.as.model.RoundId;
import org.fl.as.model.SigningSession;
import org.fl.as.store.EligibilityRegistry;
import org.fl.as.store.IssuanceLedger;
import org.fl.as.store.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sin Spring: los stores en memoria sirven directamente como dobles. */
class IssueCommitmentServiceTest {

    private static final ClientId CLIENT = ClientId.of("client-1");
    private static final RoundId ROUND = RoundId.of(1);

    private PBSDomainParams params;
    private EligibilityRegistry eligibility;
    private IssuanceLedger ledger;
    private SessionStore sessionStore;
    private IssueCommitmentService service;

    @BeforeEach
    void setUp() {
        params = PBSDomainParams.p256();
        eligibility = new EligibilityRegistry();
        ledger = new IssuanceLedger();
        sessionStore = new SessionStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-11T21:00:00Z"), ZoneOffset.UTC);
        service = new IssueCommitmentService(
            params, new SecureRandom(), eligibility, ledger, sessionStore, clock);
    }

    @Test
    void shouldIssueCommitmentDualForEligibleClient() {
        eligibility.registerAll(List.of(CLIENT), 5);

        CommitmentIssued result = service.issue(CLIENT, ROUND);

        assertThat(result.sessionId()).isNotNull();
        assertThat(result.aB64()).isNotBlank();
        assertThat(result.bB64()).isNotBlank();
        // 65 bytes en Base64 son 88 caracteres.
        assertThat(result.aB64()).hasSize(88);
        assertThat(result.bB64()).hasSize(88);
    }

    @Test
    void shouldRejectNonEligibleClient() {
        assertThatThrownBy(() -> service.issue(CLIENT, ROUND))
            .isInstanceOf(ClientNotEligibleException.class);
    }

    @Test
    void shouldRejectDuplicateRequestSameRound() {
        eligibility.registerAll(List.of(CLIENT), 5);
        service.issue(CLIENT, ROUND);

        assertThatThrownBy(() -> service.issue(CLIENT, ROUND))
            .isInstanceOf(DuplicateCredentialRequestException.class);
    }

    @Test
    void shouldRejectCredentialForConcludedRound() {
        eligibility.registerAll(List.of(ClientId.of("client-1"), ClientId.of("client-2")), 5);

        // Al pedir client-1 la ronda 2, el AS avanza activeRound a 2.
        service.issue(ClientId.of("client-1"), RoundId.of(2));

        // La ronda 1 ya concluyó: se rechaza la petición de client-2.
        assertThatThrownBy(() -> service.issue(ClientId.of("client-2"), RoundId.of(1)))
            .isInstanceOf(RoundNotActiveException.class);
    }

    @Test
    void shouldStoreSessionWithThreeValidScalars() {
        eligibility.registerAll(List.of(CLIENT), 5);
        CommitmentIssued result = service.issue(CLIENT, ROUND);

        SigningSession stored = sessionStore.takeIfPresent(result.sessionId()).orElseThrow();
        assertThat(stored.clientId()).isEqualTo(CLIENT);
        assertThat(stored.roundId()).isEqualTo(ROUND);

        assertThat(stored.u().signum()).isPositive();
        assertThat(stored.u().compareTo(params.n())).isLessThan(0);
        assertThat(stored.sAux().signum()).isPositive();
        assertThat(stored.sAux().compareTo(params.n())).isLessThan(0);
        assertThat(stored.dAux().signum()).isPositive();
        assertThat(stored.dAux().compareTo(params.n())).isLessThan(0);
    }

    @Test
    void shouldGenerateValidAPoint() {
        eligibility.registerAll(List.of(CLIENT), 5);
        CommitmentIssued result = service.issue(CLIENT, ROUND);

        ECPoint A = CryptoCodec.decodePoint(result.aB64(), params.curve());
        assertThat(A.isInfinity()).isFalse();
        assertThat(A.isValid()).isTrue();
    }

    @Test
    void shouldGenerateValidBPoint() {
        eligibility.registerAll(List.of(CLIENT), 5);
        CommitmentIssued result = service.issue(CLIENT, ROUND);

        ECPoint B = CryptoCodec.decodePoint(result.bB64(), params.curve());
        assertThat(B.isInfinity()).isFalse();
        assertThat(B.isValid()).isTrue();
    }

    @Test
    void shouldReconstructAFromStoredU() {
        eligibility.registerAll(List.of(CLIENT), 5);
        CommitmentIssued result = service.issue(CLIENT, ROUND);

        SigningSession stored = sessionStore.takeIfPresent(result.sessionId()).orElseThrow();
        ECPoint expectedA = params.G().multiply(stored.u()).normalize();
        ECPoint actualA = CryptoCodec.decodePoint(result.aB64(), params.curve());

        assertThat(actualA).isEqualTo(expectedA);
    }

    @Test
    void shouldReconstructBFromStoredSAuxAndDAux() {
        eligibility.registerAll(List.of(CLIENT), 5);
        CommitmentIssued result = service.issue(CLIENT, ROUND);

        SigningSession stored = sessionStore.takeIfPresent(result.sessionId()).orElseThrow();
        ECPoint Zr = HashToCurve.compute(ROUND.value(), params);
        ECPoint expectedB = params.G().multiply(stored.sAux())
            .add(Zr.multiply(stored.dAux())).normalize();
        ECPoint actualB = CryptoCodec.decodePoint(result.bB64(), params.curve());

        assertThat(actualB).isEqualTo(expectedB);
    }

    @Test
    void differentRoundsProduceDifferentZr() {
        ECPoint Zr1 = HashToCurve.compute(1L, params);
        ECPoint Zr2 = HashToCurve.compute(2L, params);
        assertThat(Zr1).isNotEqualTo(Zr2);
    }
}
