package org.fl.client.model;

/** Las rondas empiezan en 1. */
public record RoundId(long value) implements Comparable<RoundId> {

    public RoundId {
        if (value < 1) {
            throw new IllegalArgumentException("RoundId must be >= 1, got " + value);
        }
    }

    public static RoundId of(long value) {
        return new RoundId(value);
    }

    public RoundId next() {
        return new RoundId(value + 1);
    }

    @Override
    public int compareTo(RoundId other) {
        return Long.compare(this.value, other.value);
    }
}
