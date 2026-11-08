package org.fl.client.model;

import java.util.regex.Pattern;

/**
 * Tally Hash {@code T_r = H(h_r^1 ∥ ... ∥ h_r^{K_r})} en hex de 64 caracteres
 * minúsculos; cualquier otro formato se rechaza para que la comparación con
 * el chaincode sea exacta.
 */
public record TallyHash(String hex) {

    private static final Pattern HEX_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public TallyHash {
        if (hex == null || !HEX_PATTERN.matcher(hex).matches()) {
            throw new IllegalArgumentException(
                "TallyHash must be 64 lowercase hex chars, got: " + hex);
        }
    }

    public static TallyHash of(String hex) {
        return new TallyHash(hex);
    }
}
