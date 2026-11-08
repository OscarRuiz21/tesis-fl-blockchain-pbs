package org.fl.coordinator.gateway;

import java.util.Map;

/** Cuerpos HTTP del coordinator hacia los fl-clients. */
public final class ClientControlDtos {

    private ClientControlDtos() {
        throw new AssertionError("utility class");
    }

    /**
     * @param clientId     lo asigna el coordinator: dentro del contenedor, el
     *                     DNS de Docker no expone el alias de la réplica.
     * @param hyperparams  bloque {@code model.hyperparams} del YAML, sin interpretar.
     */
    public record StartExperimentRequest(
        String clientId,
        String experimentLabel,
        int totalRounds,
        String modelName,
        int numParams,
        String datasetPath,
        String aggregationStrategy,
        String anonymityMode,
        Map<String, Object> hyperparams
    ) {}

    public record StatusResponse(String clientId, String state) {}
}
