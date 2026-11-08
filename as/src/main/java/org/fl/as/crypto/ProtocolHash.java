package org.fl.as.crypto;

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
 *
 * <p>No hay derivación del nulificador: {@code anon_cid} funciona como tal. El
 * chaincode lo usa como sufijo de la clave del world state en su codificación
 * canónica {@code HexFormat.formatHex(anonCid.getEncoded(false))} (130
 * caracteres), en {@code deriveAnonCidHex(...)}.</p>
 */
public final class ProtocolHash {

    private static final String HASH_ALGORITHM = "SHA-256";

    private ProtocolHash() {}

    /**
     * Super-reto Fiat-Shamir, el mismo en el cegado y en la verificación de la
     * credencial:
     * <pre>
     *   ε = H(α ‖ β ‖ Z_r ‖ anon_cid)
     * </pre>
     * con los cuatro puntos sin comprimir (65 bytes cada uno), en ese orden.
     * Se devuelve sin reducir; quien necesite {@code ε mod n} lo reduce.
     *
     * @param alpha   {@code α = A + t_1·G + t_2·X_AS}, compromiso enmascarado de la rama A.
     * @param beta    {@code β = B + t_3·G + t_4·Z_r}, compromiso enmascarado de la rama B.
     * @param Zr      {@code Z_r = H_curve(r)}, anclaje público de la ronda.
     * @param anonCid {@code anon_cid = anon_sk·G}, identidad anónima del cliente.
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
     * Hashes individuales {@code h_r^k = H(Δw_r^k)} ordenados
     * lexicográficamente por su hex en minúsculas. Réplica exacta de la del
     * chaincode y la del cliente.
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
     * {@code T_r = H(h_r^1 ‖ ... ‖ h_r^K)} sobre los 32 bytes binarios de cada
     * hash, no sobre su hex.
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
     * Tally Hash de las actualizaciones, independiente del orden de recepción.
     *
     * <p>En el protocolo lo calcula el chaincode, no el AS. Esta copia solo
     * sirve para comprobar en {@code JavaPythonHashCompatibilityTest} que Java
     * y Python producen los mismos bytes, de lo que dependen la verificabilidad
     * individual (P4) y universal (P5).</p>
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
