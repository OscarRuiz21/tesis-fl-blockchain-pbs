package org.fl.client.model;

import org.fl.client.crypto.ProtocolHash;

import java.util.Arrays;
import java.util.Objects;

/**
 * Actualización local {@code Δw_r^k} en float32 big-endian. {@code H(Δw)}
 * debe coincidir byte a byte entre Java, chaincode y Python.
 */
public final class ModelUpdate {

    private final byte[] bytes;

    private ModelUpdate(byte[] alreadyCloned) {
        this.bytes = alreadyCloned;
    }

    public static ModelUpdate of(byte[] bytes) {
        Objects.requireNonNull(bytes, "ModelUpdate bytes must not be null");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("ModelUpdate must have at least one parameter");
        }
        if (bytes.length % 4 != 0) {
            throw new IllegalArgumentException(
                "ModelUpdate length must be multiple of 4 (float32), got " + bytes.length);
        }
        return new ModelUpdate(bytes.clone());
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public int numParams() {
        return bytes.length / 4;
    }

    public String sha256Hex() {
        return ProtocolHash.sha256Hex(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelUpdate other)) return false;
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "ModelUpdate(numParams=" + numParams() + ", sha256=" + sha256Hex() + ")";
    }
}
