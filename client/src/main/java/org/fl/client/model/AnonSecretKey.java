package org.fl.client.model;

import org.fl.client.crypto.PBSDomainParams;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Llave secreta anónima de una ronda, {@code anon_sk = H(master_sk ∥ r) mod n}.
 *
 * <p>Al ser determinista, la identidad anónima se recupera sin guardar estado
 * entre rondas. La reducción mod n del digest introduce un sesgo del orden de
 * 2⁻²⁵⁶, despreciable.</p>
 */
public record AnonSecretKey(BigInteger value) {

    public AnonSecretKey {
        Objects.requireNonNull(value, "AnonSecretKey value must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("AnonSecretKey must be positive");
        }
    }

    /**
     * {@code anon_sk = SHA-256(master_sk ∥ roundId_BE8) mod n}. La ronda va en
     * 8 bytes big-endian, igual que en el chaincode.
     */
    public static AnonSecretKey derive(MasterSecret master, RoundId round, PBSDomainParams params) {
        Objects.requireNonNull(master, "master must not be null");
        Objects.requireNonNull(round, "round must not be null");
        Objects.requireNonNull(params, "params must not be null");
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(master.bytes());
            sha.update(ByteBuffer.allocate(8).putLong(round.value()).array());
            byte[] digest = sha.digest();
            BigInteger reduced = new BigInteger(1, digest).mod(params.n());
            if (reduced.signum() == 0) {
                // Probabilidad del orden de 2⁻²⁵⁶; cualquier escalar válido sirve.
                reduced = BigInteger.ONE;
            }
            return new AnonSecretKey(reduced);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
