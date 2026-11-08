package org.fl.as.service;

import org.fl.as.crypto.CryptoCodec;
import org.fl.as.crypto.PBSDomainParams;
import org.fl.as.exception.SessionNotFoundException;
import org.fl.as.model.ASKeyPair;
import org.fl.as.model.ClientId;
import org.fl.as.model.RoundId;
import org.fl.as.model.SessionId;
import org.fl.as.model.SignatureIssued;
import org.fl.as.model.SigningSession;
import org.fl.as.store.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueSignatureServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-11T21:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);

    private PBSDomainParams params;
    private ASKeyPair keyPair;
    private SessionStore sessionStore;
    private Clock clock;
    private IssueSignatureService service;

    @BeforeEach
    void setUp() {
        params = PBSDomainParams.p256();
        keyPair = ASKeyPair.generate(params, new SecureRandom());
        sessionStore = new SessionStore();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new IssueSignatureService(params, keyPair, sessionStore, TTL, clock);
    }

    @Test
    void shouldReturnFourScalarsForValidSession() {
        SigningSession session = newSession(NOW);
        sessionStore.put(session);

        BigInteger e = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        SignatureIssued result = service.sign(
            session.sessionId(), CryptoCodec.encodeScalar(e));

        assertThat(result.rAnsB64()).isNotBlank();
        assertThat(result.cB64()).isNotBlank();
        assertThat(result.sB64()).isNotBlank();
        assertThat(result.dB64()).isNotBlank();
    }

    @Test
    void shouldThrowWhenSessionNotFound() {
        SessionId unknown = SessionId.random();
        assertThatThrownBy(() -> service.sign(unknown, CryptoCodec.encodeScalar(BigInteger.ONE)))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void shouldThrowWhenSessionExpired() {
        // Sesión creada hace 10 min (más allá del TTL de 5 min).
        Instant past = NOW.minus(Duration.ofMinutes(10));
        SigningSession expired = newSession(past);
        sessionStore.put(expired);

        assertThatThrownBy(() -> service.sign(
            expired.sessionId(), CryptoCodec.encodeScalar(BigInteger.ONE)))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void shouldConsumeSessionAtomically() {
        SigningSession session = newSession(NOW);
        sessionStore.put(session);

        service.sign(session.sessionId(), CryptoCodec.encodeScalar(BigInteger.ONE));

        // La segunda llamada con el mismo sessionId falla: la sesión fue consumida.
        assertThatThrownBy(() -> service.sign(
            session.sessionId(), CryptoCodec.encodeScalar(BigInteger.ONE)))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void shouldComputeCAndRAnsCorrectly() {
        // c = e - d mod n,  r_ans = u - c·x_AS mod n
        BigInteger u    = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        BigInteger sAux = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        BigInteger dAux = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        SessionId sid = SessionId.random();
        sessionStore.put(new SigningSession(
            sid, u, sAux, dAux, ClientId.of("client-1"), RoundId.of(1), NOW));

        BigInteger e = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        SignatureIssued result = service.sign(sid, CryptoCodec.encodeScalar(e));

        BigInteger expectedC = e.subtract(dAux).mod(params.n());
        BigInteger expectedRAns = u.subtract(expectedC.multiply(keyPair.privateKey())).mod(params.n());

        assertThat(CryptoCodec.decodeScalar(result.cB64(), params.n())).isEqualTo(expectedC);
        assertThat(CryptoCodec.decodeScalar(result.rAnsB64(), params.n())).isEqualTo(expectedRAns);
    }

    @Test
    void shouldReturnSAuxAndDAuxFromSession() {
        BigInteger u    = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        BigInteger sAux = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        BigInteger dAux = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        SessionId sid = SessionId.random();
        sessionStore.put(new SigningSession(
            sid, u, sAux, dAux, ClientId.of("client-1"), RoundId.of(1), NOW));

        BigInteger e = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        SignatureIssued result = service.sign(sid, CryptoCodec.encodeScalar(e));

        assertThat(CryptoCodec.decodeScalar(result.sB64(), params.n())).isEqualTo(sAux);
        assertThat(CryptoCodec.decodeScalar(result.dB64(), params.n())).isEqualTo(dAux);
    }

    private SigningSession newSession(Instant createdAt) {
        BigInteger u    = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        BigInteger sAux = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        BigInteger dAux = CryptoCodec.randomScalar(params.n(), new SecureRandom());
        return new SigningSession(
            SessionId.random(), u, sAux, dAux,
            ClientId.of("client-1"), RoundId.of(1), createdAt);
    }
}
