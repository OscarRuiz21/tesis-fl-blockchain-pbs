package org.fl.client.model;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.client.crypto.CryptoCodec;
import org.fl.client.crypto.PBSDomainParams;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Identidad anónima de una ronda: el punto {@code anon_cid = anon_sk · G}.
 *
 * <p>El mismo punto entra al reto Fiat-Shamir
 * {@code ε = H(α ‖ β ‖ Z_r ‖ anon_cid)}, es la llave pública con la que el
 * chaincode verifica {@code σ_model} (P6) y es el nulificador: su hex sin
 * comprimir es el sufijo de la clave en el world state.</p>
 *
 * <p>La codificación canónica es sin comprimir ({@code 0x04 ‖ x ‖ y}, 65
 * bytes), la misma de {@link CryptoCodec#encodePoint} en cliente, AS y
 * chaincode.</p>
 */
public final class AnonId {

    private final ECPoint point;

    private AnonId(ECPoint normalized) {
        this.point = normalized;
    }

    public static AnonId of(ECPoint point) {
        Objects.requireNonNull(point, "AnonId point must not be null");
        if (point.isInfinity()) {
            throw new IllegalArgumentException("AnonId cannot be the point at infinity");
        }
        if (!point.isValid()) {
            throw new IllegalArgumentException("AnonId point is not on the curve");
        }
        return new AnonId(point.normalize());
    }

    public static AnonId fromBase64(String b64, PBSDomainParams params) {
        Objects.requireNonNull(b64, "Base64 must not be null");
        Objects.requireNonNull(params, "params must not be null");
        return of(CryptoCodec.decodePoint(b64, params.curve()));
    }

    public ECPoint point() {
        return point;
    }

    public byte[] toUncompressedBytes() {
        return point.getEncoded(false);
    }

    public String toBase64() {
        return CryptoCodec.encodePoint(point);
    }

    /**
     * Hex en minúsculas de la forma sin comprimir (130 caracteres). Es el
     * sufijo de las claves del ledger que usan {@code anon_cid} como
     * nulificador; la ronda queda ligada porque {@code anon_sk = H(master_sk ∥ r)}.
     */
    public String toUncompressedHex() {
        return HexFormat.of().formatHex(toUncompressedBytes());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnonId other)) return false;
        return Arrays.equals(toUncompressedBytes(), other.toUncompressedBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(toUncompressedBytes());
    }

    @Override
    public String toString() {
        return "AnonId(point=<EC>)";
    }
}
