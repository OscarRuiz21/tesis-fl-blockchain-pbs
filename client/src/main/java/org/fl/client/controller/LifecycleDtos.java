package org.fl.client.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/** Contrato HTTP de {@code /lifecycle} con el coordinator. */
public final class LifecycleDtos {

    private LifecycleDtos() {}

    /**
     * {@code hyperparams} es opaco para Java; los valida el script de Python.
     * {@code datasetPath} es la ruta absoluta a la partición CSV del cliente.
     */
    public record StartExperimentRequest(
        @NotBlank String clientId,
        @NotBlank String experimentLabel,
        @Min(1) int totalRounds,
        @NotBlank String modelName,
        @Min(1) int numParams,
        @NotBlank String datasetPath,
        @NotBlank String aggregationStrategy,
        @NotBlank @Pattern(regexp = "pbs|none", message = "must be 'pbs' or 'none'")
        String anonymityMode,
        Map<String, Object> hyperparams
    ) {}

    /** {@code clientId} es null mientras el coordinator no lo asigne. */
    public record StatusResponse(String clientId, String state) {}

    public record StopRequest(boolean force) {}
}
