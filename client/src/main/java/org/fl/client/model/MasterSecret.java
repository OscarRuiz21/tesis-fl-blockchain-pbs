package org.fl.client.model;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Secreto maestro de 32 bytes, generado una vez y guardado localmente. De él
 * se deriva {@code anon_sk = H(master_sk ∥ r)} en cada ronda, así que el
 * cliente regenera su identidad anónima sin estado intermedio.
 */
public final class MasterSecret {

    public static final int LENGTH = 32;

    private final byte[] bytes;

    private MasterSecret(byte[] alreadyCloned) {
        this.bytes = alreadyCloned;
    }

    public static MasterSecret fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "MasterSecret bytes must not be null");
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException(
                "MasterSecret must be " + LENGTH + " bytes, got " + bytes.length);
        }
        return new MasterSecret(bytes.clone());
    }

    public static MasterSecret generate(SecureRandom random) {
        Objects.requireNonNull(random, "SecureRandom must not be null");
        byte[] fresh = new byte[LENGTH];
        random.nextBytes(fresh);
        return new MasterSecret(fresh);
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MasterSecret other)) return false;
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "MasterSecret(" + HexFormat.of().formatHex(new byte[]{bytes[0]}) + "…<redacted>)";
    }
}
