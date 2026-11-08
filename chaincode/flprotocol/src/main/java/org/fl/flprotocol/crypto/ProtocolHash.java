package org.fl.flprotocol.crypto;

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
 * Funciones hash del protocolo: super-reto Fiat-Shamir de la PBS y Tally Hash.
 * Deben producir los mismos bytes que {@code python/tests/compute_hash.py} y
 * {@code compute_tally_hash.py}.
 *
 * <p>El nulificador no se deriva aquí: el propio {@code anon_cid} hace de
 * nulificador y su codificación canónica en el World State es
 * {@code HexFormat.formatHex(anonCid.getEncoded(false))} (130 caracteres en
 * P-256), calculada en {@link org.fl.flprotocol.FLProtocolChaincode}.</p>
 */
public final class ProtocolHash {

    private static final String HASH_ALGORITHM = "SHA-256";

    private ProtocolHash() {}

    /**
     * Super-reto Fiat-Shamir de la PBS, el mismo en el cegado y en la
     * verificación de la credencial:
     * <pre>
     *   ε = H(α ‖ β ‖ Z_r ‖ anon_cid)
     * </pre>
     *
     * <p>Los cuatro puntos se concatenan en ese orden, cada uno sin comprimir
     * (65 bytes en P-256). El resultado es un {@link BigInteger} positivo sin
     * reducir; quien necesite {@code ε mod n} aplica {@code .mod(n)}.</p>
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
     * Hashes individuales {@code h_r^k = H(Δw_r^k)} en orden lexicográfico de
     * su hex en minúsculas. Ordenar por el hash vuelve el resultado
     * independiente del orden en que los updates llegaron al ledger.
     *
     * @param updates bytes de las actualizaciones, en cualquier orden.
     * @return hashes en hex en minúsculas (64 caracteres cada uno), ordenados.
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
     * Tally Hash a partir de la lista ya ordenada:
     * {@code T_r = H(h_r^1 ‖ h_r^2 ‖ ... ‖ h_r^K)}.
     *
     * <p>El orden se toma del hex, pero cada {@code h_r^k} entra al digest como
     * sus 32 bytes binarios, no como texto hex.</p>
     *
     * @param sortedHashesHex hashes ordenados, en hex en minúsculas.
     * @return Tally Hash en hex en minúsculas (64 caracteres).
     */
    public static String computeTallyHashHexFromSortedHashes(List<String> sortedHashesHex) {
        MessageDigest digest = sha256();
        HexFormat hex = HexFormat.of();
        for (String h : sortedHashesHex) {
            digest.update(hex.parseHex(h));
        }
        return hex.formatHex(digest.digest());
    }

    /**
     * Tally Hash directo desde los updates, sin exponer la lista ordenada
     * intermedia. El contrato usa las dos funciones por separado porque además
     * persiste esa lista.
     */
    public static String computeTallyHashHex(List<byte[]> updates) {
        return computeTallyHashHexFromSortedHashes(computeSortedUpdateHashesHex(updates));
    }

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
