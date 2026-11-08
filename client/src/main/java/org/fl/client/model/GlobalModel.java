package org.fl.client.model;

import org.fl.client.crypto.ProtocolHash;

import java.util.Arrays;
import java.util.Objects;

/**
 * Modelo global {@code w_r} del ledger, en float32 big-endian, con el Tally
 * Hash de la ronda anterior (null en la ronda 1).
 */
public final class GlobalModel {

    private final byte[] bytes;
    private final RoundId round;
    private final TallyHash previousTally;
    /** {@code H(w_r)} publicado en el ledger, o null si no vino. */
    private final String publishedModelHash;

    private GlobalModel(byte[] alreadyCloned, RoundId round, TallyHash previousTally,
                        String publishedModelHash) {
        this.bytes = alreadyCloned;
        this.round = round;
        this.previousTally = previousTally;
        this.publishedModelHash = publishedModelHash;
    }

    public static GlobalModel of(byte[] bytes, RoundId round, TallyHash previousTally) {
        return of(bytes, round, previousTally, null);
    }

    public static GlobalModel of(byte[] bytes, RoundId round, TallyHash previousTally,
                                 String publishedModelHash) {
        Objects.requireNonNull(bytes, "GlobalModel bytes must not be null");
        Objects.requireNonNull(round, "round must not be null");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("GlobalModel must have at least one parameter");
        }
        if (bytes.length % 4 != 0) {
            throw new IllegalArgumentException(
                "GlobalModel length must be multiple of 4 (float32), got " + bytes.length);
        }
        if (round.value() == 1 && previousTally != null) {
            throw new IllegalArgumentException("Round 1 must have null previousTally");
        }
        if (round.value() > 1 && previousTally == null) {
            throw new IllegalArgumentException("Rounds > 1 must have non-null previousTally");
        }
        return new GlobalModel(bytes.clone(), round, previousTally, publishedModelHash);
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public RoundId round() {
        return round;
    }

    public TallyHash previousTally() {
        return previousTally;
    }

    /** La auditoría lo compara con {@link #sha256Hex()} antes de usar el modelo. */
    public String publishedModelHash() {
        return publishedModelHash;
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
        if (!(o instanceof GlobalModel other)) return false;
        return Arrays.equals(bytes, other.bytes)
            && round.equals(other.round)
            && Objects.equals(previousTally, other.previousTally);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(bytes), round, previousTally);
    }

    @Override
    public String toString() {
        return "GlobalModel(round=" + round.value()
            + ", numParams=" + numParams()
            + ", sha256=" + sha256Hex()
            + ", previousTally=" + (previousTally == null ? "<null>" : previousTally.hex())
            + ")";
    }
}
