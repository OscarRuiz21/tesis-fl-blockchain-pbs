package org.fl.as.service;

import lombok.RequiredArgsConstructor;
import org.fl.as.crypto.CryptoCodec;
import org.fl.as.crypto.PBSDomainParams;
import org.fl.as.exception.SessionNotFoundException;
import org.fl.as.model.ASKeyPair;
import org.fl.as.model.SessionId;
import org.fl.as.model.SignatureIssued;
import org.fl.as.model.SigningSession;
import org.fl.as.store.SessionStore;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;

/**
 * Sign de la PBS. Con el desafío cegado {@code e} del cliente calcula
 * <pre>
 *   c     = e - d_aux mod n
 *   r_ans = u - c·x_AS mod n
 * </pre>
 * y devuelve {@code (r_ans, c, s_aux, d_aux)}, que el cliente descega con
 * {@code t_1..t_4} para obtener {@code (ρ, ω, σ, δ)}.
 *
 * <p>La sesión se consume atómicamente antes de firmar, así que un segundo
 * {@code sign} con el mismo {@code sessionId} falla: firmar dos retos con el
 * mismo {@code u} revelaría {@code x_AS}. Una sesión expirada se reporta como
 * inexistente.</p>
 */
@Service
@RequiredArgsConstructor
public class IssueSignatureService {

    private final PBSDomainParams params;
    private final ASKeyPair keyPair;
    private final SessionStore sessionStore;
    private final Duration sessionTtl;
    private final Clock clock;

    public SignatureIssued sign(SessionId sessionId, String eB64) {
        BigInteger e = CryptoCodec.decodeScalar(eB64, params.n());

        SigningSession session = sessionStore.takeIfPresent(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.isExpired(sessionTtl, clock.instant())) {
            throw new SessionNotFoundException(sessionId);
        }

        BigInteger c = e.subtract(session.dAux()).mod(params.n());

        BigInteger rAns = session.u()
            .subtract(c.multiply(keyPair.privateKey()))
            .mod(params.n());

        return new SignatureIssued(
            CryptoCodec.encodeScalar(rAns),
            CryptoCodec.encodeScalar(c),
            CryptoCodec.encodeScalar(session.sAux()),
            CryptoCodec.encodeScalar(session.dAux())
        );
    }
}
