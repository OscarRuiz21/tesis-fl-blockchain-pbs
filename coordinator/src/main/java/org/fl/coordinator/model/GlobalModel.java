package org.fl.coordinator.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Modelo global de una ronda en el formato binario que comparten Java, Python
 * y el chaincode (float32 big-endian).
 *
 * @param previousTallyHash  Tally Hash de la ronda anterior; {@code null} en la ronda 1.
 */
public record GlobalModel(byte[] bytes, RoundId roundId, TallyHash previousTallyHash) {

    public GlobalModel {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.requireNonNull(roundId, "roundId must not be null");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must be non-empty");
        }
        bytes = bytes.clone();
    }

    public static GlobalModel of(byte[] bytes, RoundId roundId, TallyHash previousTallyHash) {
        return new GlobalModel(bytes, roundId, previousTallyHash);
    }

    // Devuelve una copia: el mismo modelo se usa para evaluar, escribir a disco y serializar.
    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GlobalModel other)) return false;
        return roundId.equals(other.roundId)
            && Objects.equals(previousTallyHash, other.previousTallyHash)
            && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roundId, previousTallyHash, Arrays.hashCode(bytes));
    }
}
