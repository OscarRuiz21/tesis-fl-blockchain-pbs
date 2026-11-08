package org.fl.coordinator.model;

/**
 * Tally Hash de una ronda (SHA-256 en hex) que el chaincode publica en
 * {@code aggregateRound}. El coordinator no lo recalcula, solo lo conserva
 * junto al modelo de la ronda siguiente.
 */
public record TallyHash(String hex) {

    public TallyHash {
        if (hex == null || hex.isBlank()) {
            throw new IllegalArgumentException("TallyHash hex must be non-null and non-blank");
        }
    }

    public static TallyHash of(String hex) {
        return new TallyHash(hex);
    }
}
