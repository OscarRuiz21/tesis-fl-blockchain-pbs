package org.fl.client.crypto;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Funciones hash del protocolo: el super-reto Fiat-Shamir de la PBS y el
 * Tally Hash.
 *
 * <p>El cálculo del Tally Hash es idéntico byte a byte al del chaincode
 * ({@code org.fl.flprotocol.crypto.ProtocolHash}): {@code h_r^k = H(Δw_r^k)},
 * orden lexicográfico por hex y {@code T_r = H(h_r^1 ‖ ... ‖ h_r^K)}
 * sobre los 32 bytes binarios de cada hash. El cliente lo recalcula en la
 * auditoría P4/P5.</p>
 */
public final class ProtocolHash {

    private static final String HASH_ALGORITHM = "SHA-256";

    private ProtocolHash() {}

    /**
     * Calcula el super-reto Fiat-Shamir del esquema Abe-Okamoto, usado tanto
     * en el cegado como en la verificación de la credencial:
     * <pre>
     *   ε = H(α ‖ β ‖ Z_r ‖ anon_cid)
     * </pre>
     *
     * <p>Cada punto entra sin comprimir (65 bytes en P-256), en ese orden.
     * El resultado no se reduce mod n; quien necesite {@code ε mod n} lo
     * reduce explícitamente.</p>
     *
     * @param alpha   {@code α = A + t_1·G + t_2·X_AS} (compromiso enmascarado de la Rama A).
     * @param beta    {@code β = B + t_3·G + t_4·Z_r} (compromiso enmascarado de la Rama B).
     * @param Zr      {@code Z_r = H_curve(r)} (anclaje público de la ronda).
     * @param anonCid {@code anon_cid = anon_sk · G} (identidad anónima del cliente).
     */
    public static BigInteger computeAbeOkamotoChallenge(
            ECPoint alpha, ECPoint beta, ECPoint Zr, ECPoint anonCid) {
        Objects.requireNonNull(alpha, "alpha must not be null");
        Objects.requireNonNull(beta, "beta must not be null");
        Objects.requireNonNull(Zr, "Zr must not be null");
        Objects.requireNonNull(anonCid, "anonCid must not be null");

        MessageDigest digest = sha256();
        digest.update(alpha.normalize().getEncoded(false));
        digest.update(beta.normalize().getEncoded(false));
        digest.update(Zr.normalize().getEncoded(false));
        digest.update(anonCid.normalize().getEncoded(false));
        return new BigInteger(1, digest.digest());
    }

    /**
     * Hashes individuales {@code h_r^k = H(Δw_r^k)} ordenados por su hex en
     * minúsculas; el orden hace el resultado independiente del orden de
     * recepción.
     */
    public static List<String> computeSortedUpdateHashesHex(List<byte[]> updates) {
        List<String> hashes = new ArrayList<>(updates.size());
        for (byte[] update : updates) {
            hashes.add(sha256Hex(update));
        }
        hashes.sort(Comparator.naturalOrder());
        return List.copyOf(hashes);
    }

    /**
     * Tally Hash a partir de los hashes ya ordenados:
     * {@code T_r = H(h_r^1 ‖ h_r^2 ‖ ... ‖ h_r^K)}.
     *
     * <p>Cada {@code h_r^k} se concatena en sus 32 bytes binarios, no en hex
     * ASCII, igual que en el chaincode. La auditoría P5 lo usa para comparar
     * el resultado sobre {@code queryUpdateHashes} con el {@code tally_<r>}
     * del ledger.</p>
     */
    public static String computeTallyHashHexFromSortedHashes(List<String> sortedHashesHex) {
        MessageDigest digest = sha256();
        HexFormat hex = HexFormat.of();
        for (String h : sortedHashesHex) {
            digest.update(hex.parseHex(h));
        }
        return hex.formatHex(digest.digest());
    }

    public static String computeTallyHashHex(List<byte[]> updates) {
        return computeTallyHashHexFromSortedHashes(computeSortedUpdateHashesHex(updates));
    }

    /** SHA-256 en hex minúsculas (64 caracteres). */
    public static String sha256Hex(byte[] data) {
        return HexFormat.of().formatHex(sha256().digest(data));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
