package org.fl.client.service;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.client.crypto.ModelSigner;
import org.fl.client.crypto.PBSClientOperations;
import org.fl.client.crypto.PBSDomainParams;
import org.fl.client.exception.InvalidPBSSignatureException;
import org.fl.client.gateway.ASGateway;
import org.fl.client.model.AnonId;
import org.fl.client.model.AnonSecretKey;
import org.fl.client.model.BlindResult;
import org.fl.client.model.ClientId;
import org.fl.client.model.Commitment;
import org.fl.client.model.Credential;
import org.fl.client.model.MasterSecret;
import org.fl.client.model.PhaseTimings;
import org.fl.client.model.PreparedSubmission;
import org.fl.client.model.RoundId;
import org.fl.client.model.SignatureResponse;
import org.fl.client.time.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Etapa 1 del protocolo en sus dos modos. {@code "pbs"} ejecuta la emisión
 * completa de la credencial con el AS y firma {@code σ_model}. {@code "none"}
 * es la línea base sin anonimato: {@code anon_cid} se deriva de
 * {@code (clientId, roundId)} y la credencial es trivial; requiere el
 * chaincode con {@code skip_pbs=true}.
 *
 * <p>El modo se fija en cada {@code /lifecycle/start}, así que el mismo
 * contenedor sirve para corridas {@code pbs} y {@code none}.</p>
 *
 * <p>Los tiempos se toman con {@link TimeSource#nanos()}: con el reloj de
 * pared, operaciones como el descegado se truncaban a 0 ms.</p>
 */
@Service
public class AnonymityService {

    private static final Logger log = LoggerFactory.getLogger(AnonymityService.class);

    /** Deben coincidir con el {@code @Pattern} de {@code StartExperimentRequest}. */
    private static final String MODE_PBS = "pbs";
    private static final String MODE_NONE = "none";

    private final PBSDomainParams params;
    private final SecureRandom random;
    private final ASGateway asGateway;
    private final MasterSecret masterSecret;
    private final TimeSource time;
    private final AtomicReference<String> mode = new AtomicReference<>();

    public AnonymityService(PBSDomainParams params,
                            SecureRandom random,
                            ASGateway asGateway,
                            MasterSecret masterSecret,
                            TimeSource time,
                            @Value("${fl.anonymity-mode:pbs}") String defaultMode) {
        this.params = params;
        this.random = random;
        this.asGateway = asGateway;
        this.masterSecret = masterSecret;
        this.time = time;
        this.mode.set(validate(defaultMode));
    }

    /** Se invoca solo tras ganar el CAS a {@code RUNNING}. */
    public void applyMode(String requested) {
        String validated = validate(requested);
        String prev = mode.getAndSet(validated);
        if (!prev.equals(validated)) {
            log.info("AnonymityService mode changed: {} -> {}", prev, validated);
        }
    }

    public PreparedSubmission prepareSubmission(ClientId clientId, RoundId roundId) {
        if (MODE_PBS.equals(mode.get())) {
            return preparePbs(clientId, roundId);
        }
        return prepareNone(clientId, roundId);
    }

    /**
     * {@code σ_model} sobre {@code Δw} para P6. En modo {@code none} devuelve
     * un arreglo vacío porque el chaincode con {@code skip_pbs=true} no
     * verifica P6.
     */
    public byte[] signUpdate(RoundId roundId, byte[] update) {
        if (MODE_PBS.equals(mode.get())) {
            return signPbs(roundId, update);
        }
        return signNone();
    }

    public String mode() {
        return mode.get();
    }

    private static String validate(String m) {
        if (!MODE_PBS.equals(m) && !MODE_NONE.equals(m)) {
            throw new IllegalArgumentException(
                "anonymityMode must be '" + MODE_PBS + "' or '" + MODE_NONE + "', got: " + m);
        }
        return m;
    }

    private PreparedSubmission preparePbs(ClientId clientId, RoundId roundId) {
        AnonId anonCid = PBSClientOperations.deriveAnonId(masterSecret, roundId, params);

        // Solo la ronda 1 hace el GET de X_AS. Se mide aparte para que no
        // infle round_wall_time sin aparecer en ninguna columna.
        long tFetch = time.nanos();
        ECPoint xAS = asGateway.fetchServerPublicKey();
        Duration fetchPubKeyRtt = Duration.ofNanos(time.nanos() - tFetch);

        long t0 = time.nanos();
        var commitSession = asGateway.requestCommitment(clientId, roundId);
        Duration commitRtt = Duration.ofNanos(time.nanos() - t0);

        long t1 = time.nanos();
        Commitment commit = commitSession.commitment();
        BlindResult blind = PBSClientOperations.blind(
            commit.A(), commit.B(), anonCid, roundId, xAS, params, random);
        Duration blindD = Duration.ofNanos(time.nanos() - t1);

        // El AS firma e sin ver α, β ni anon_cid.
        long t2 = time.nanos();
        SignatureResponse sigResp = asGateway.requestSignature(
            commitSession.sessionId(), blind.e());
        Duration signRtt = Duration.ofNanos(time.nanos() - t2);

        long t3 = time.nanos();
        Credential credential = PBSClientOperations.unblind(sigResp, blind, params.n());
        Duration unblindD = Duration.ofNanos(time.nanos() - t3);

        // Si falla aquí también fallaría en el chaincode.
        long t4 = time.nanos();
        if (!PBSClientOperations.verifyLocally(credential, anonCid, roundId, xAS, params)) {
            throw new InvalidPBSSignatureException(anonCid, roundId);
        }
        Duration verifyD = Duration.ofNanos(time.nanos() - t4);

        PhaseTimings timings = new PhaseTimings(
            blindD, unblindD, verifyD, commitRtt, signRtt, fetchPubKeyRtt);

        return new PreparedSubmission(anonCid, credential, timings);
    }

    private byte[] signPbs(RoundId roundId, byte[] update) {
        // La derivación determinista asegura que anon_sk corresponde al
        // anon_cid enviado.
        AnonSecretKey anonSk = AnonSecretKey.derive(masterSecret, roundId, params);
        return ModelSigner.sign(anonSk.value(), update, params, random);
    }

    private PreparedSubmission prepareNone(ClientId clientId, RoundId roundId) {
        // Línea base: anon_cid se deriva de (clientId, roundId) sin master_sk,
        // así que el chaincode puede vincularlo con el cliente. Multiplicar
        // por G da un punto válido.
        BigInteger pseudoSk = pseudoScalar(clientId, roundId);
        ECPoint anonCidPoint = params.G().multiply(pseudoSk).normalize();
        AnonId anonCid = AnonId.of(anonCidPoint);

        // El chaincode con skip_pbs=true no verifica la credencial.
        Credential trivial = new Credential(
            BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE);

        return new PreparedSubmission(anonCid, trivial, PhaseTimings.zero());
    }

    private byte[] signNone() {
        return new byte[0];
    }

    private BigInteger pseudoScalar(ClientId clientId, RoundId roundId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest((clientId.value() + ":" + roundId.value()).getBytes());
            BigInteger reduced = new BigInteger(1, hash).mod(params.n());
            if (reduced.signum() == 0) return BigInteger.ONE;
            return reduced;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
