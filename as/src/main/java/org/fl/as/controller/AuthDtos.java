package org.fl.as.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** DTOs HTTP del AS. */
public final class AuthDtos {

    private AuthDtos() {
        throw new AssertionError("utility class");
    }

    /** Reemplaza la whitelist anterior; {@code experimentId} solo se registra en el log. */
    public record AdminInitRequest(
        @NotEmpty List<String> allowedClients,
        @Min(1) int totalRounds,
        @NotBlank String experimentId
    ) {}

    public record CommitRequest(
        @NotBlank String clientId,
        @Min(1) long roundId
    ) {}

    /**
     * Compromisos {@code A = u·G} (rama A) y {@code B = sAux·G + dAux·Z_r}
     * (rama B), en Base64 sin comprimir.
     */
    public record CommitResponse(String sessionId, String aB64, String bB64) {}

    /** Desafío cegado {@code e = ε - t_2 - t_4 mod n} para la sesión abierta en {@code /commit}. */
    public record SignRequest(
        @NotBlank String sessionId,
        @NotBlank String eB64
    ) {}

    /**
     * Escalares de 32 bytes big-endian en Base64: {@code r_ans = u - c·x_AS mod n}
     * (respuesta honesta de la rama A), {@code c = e - d mod n}, y los valores
     * {@code s}, {@code d} de la rama B simulada, fijados en el commit.
     */
    public record SignResponse(String rAnsB64, String cB64, String sB64, String dB64) {}

    /** {@code X_AS = x_AS·G} en Base64 sin comprimir (65 bytes). */
    public record PublicKeyResponse(String publicKeyB64) {}
}
