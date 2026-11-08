package org.fl.client.gateway;

/** Cuerpos HTTP de {@code /blind-sign/*} del AS. */
public final class ASDtos {

    private ASDtos() {}

    public record CommitReq(String clientId, long roundId) {}

    /** {@code A = u·G} y {@code B = sAux·G + dAux·Z_r} en Base64. */
    public record CommitResult(String sessionId, String aB64, String bB64) {}

    /** {@code eB64} es el reto cegado {@code e = ε - t_2 - t_4 mod n}. */
    public record SignReq(String sessionId, String eB64) {}

    public record SignResult(String rAnsB64, String cB64, String sB64, String dB64) {}

    public record PubKeyResult(String publicKeyB64) {}
}
