package org.fl.as.service;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.math.ec.ECPoint;
import org.fl.as.crypto.CryptoCodec;
import org.fl.as.crypto.HashToCurve;
import org.fl.as.crypto.PBSDomainParams;
import org.fl.as.exception.ClientNotEligibleException;
import org.fl.as.exception.DuplicateCredentialRequestException;
import org.fl.as.exception.RoundNotActiveException;
import org.fl.as.model.ClientId;
import org.fl.as.model.CommitmentIssued;
import org.fl.as.model.IssuanceRecord;
import org.fl.as.model.RoundId;
import org.fl.as.model.SessionId;
import org.fl.as.model.SigningSession;
import org.fl.as.store.EligibilityRegistry;
import org.fl.as.store.IssuanceLedger;
import org.fl.as.store.SessionStore;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;

/**
 * Commit de la PBS: tras validar elegibilidad, ronda y unicidad, muestrea
 * {@code u, sAux, dAux} y devuelve el compromiso dual {@code A = u·G} (rama A,
 * honesta) y {@code B = sAux·G + dAux·Z_r} (rama B, simulada). No usa
 * {@code x_AS}; la respuesta con la llave ocurre en {@link IssueSignatureService}.
 */
@Service
@RequiredArgsConstructor
public class IssueCommitmentService {

    private final PBSDomainParams params;
    private final SecureRandom random;
    private final EligibilityRegistry eligibilityRegistry;
    private final IssuanceLedger issuanceLedger;
    private final SessionStore sessionStore;
    private final Clock clock;

    public CommitmentIssued issue(ClientId clientId, RoundId roundId) {
        if (!eligibilityRegistry.isEligible(clientId, roundId)) {
            throw new ClientNotEligibleException(clientId);
        }

        // No se emiten credenciales para rondas concluidas. El AS infiere la
        // ronda activa de las propias peticiones: un cliente solo pide la ronda
        // r tras observar que el ledger avanzó a r, y nadie necesita una
        // credencial anterior a activeRound. El coordinator no interviene.
        if (eligibilityRegistry.isRoundConcluded(roundId)) {
            throw new RoundNotActiveException(roundId, eligibilityRegistry.activeRound());
        }
        eligibilityRegistry.advanceActiveRound(roundId.value());

        // Atómico: dos peticiones concurrentes del mismo cliente no pasan ambas.
        if (!issuanceLedger.tryRecord(new IssuanceRecord(clientId, roundId))) {
            throw new DuplicateCredentialRequestException(clientId, roundId);
        }

        ECPoint Zr = HashToCurve.compute(roundId.value(), params);

        BigInteger u    = CryptoCodec.randomScalar(params.n(), random);
        BigInteger sAux = CryptoCodec.randomScalar(params.n(), random);
        BigInteger dAux = CryptoCodec.randomScalar(params.n(), random);

        // B se construye en reversa: se fijan primero la respuesta y el reto simulados.
        ECPoint A = params.G().multiply(u).normalize();
        ECPoint B = params.G().multiply(sAux).add(Zr.multiply(dAux)).normalize();

        SessionId sessionId = SessionId.random();
        sessionStore.put(new SigningSession(
            sessionId, u, sAux, dAux, clientId, roundId, clock.instant()));

        return new CommitmentIssued(sessionId, CryptoCodec.encodePoint(A), CryptoCodec.encodePoint(B));
    }
}
