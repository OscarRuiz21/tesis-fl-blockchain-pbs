package org.fl.coordinator.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Especificación de un experimento, con la misma estructura que su YAML.
 *
 * <p>Los hiperparámetros del modelo van en un mapa opaco que el coordinator
 * reenvía sin interpretar al fl-client y a {@code eval.py}. Así, un modelo
 * nuevo solo requiere registrarlo en Python y en la whitelist de
 * {@code SpecValidator}.</p>
 *
 * @param datasetBasePath  directorio de particiones; cada cliente lee {@code client-<i>.csv}.
 * @param dirichletAlpha   concentración de Dirichlet, {@code null} con partición IID.
 */
public record ExperimentSpec(
    String label,
    int k,
    int totalRounds,
    int seed,
    String aggregationStrategy,
    String anonymityMode,
    String modelName,
    int numParams,
    String datasetType,
    Path datasetBasePath,
    Path testSetPath,
    String partitionStrategy,
    Double dirichletAlpha,
    Map<String, Object> hyperparams,
    Duration tTrainSeconds,
    Duration tRoundSeconds,
    Duration tExperimentSeconds,
    Path resultsDir
) {
    public ExperimentSpec {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(aggregationStrategy, "aggregationStrategy must not be null");
        Objects.requireNonNull(anonymityMode, "anonymityMode must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(datasetType, "datasetType must not be null");
        Objects.requireNonNull(datasetBasePath, "datasetBasePath must not be null");
        Objects.requireNonNull(testSetPath, "testSetPath must not be null");
        Objects.requireNonNull(partitionStrategy, "partitionStrategy must not be null");
        Objects.requireNonNull(hyperparams, "hyperparams must not be null");
        Objects.requireNonNull(tTrainSeconds, "tTrainSeconds must not be null");
        Objects.requireNonNull(tRoundSeconds, "tRoundSeconds must not be null");
        Objects.requireNonNull(tExperimentSeconds, "tExperimentSeconds must not be null");
        Objects.requireNonNull(resultsDir, "resultsDir must not be null");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must be non-blank");
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1, got " + k);
        }
        if (totalRounds < 1) {
            throw new IllegalArgumentException("totalRounds must be >= 1, got " + totalRounds);
        }
        if (numParams < 1) {
            throw new IllegalArgumentException("numParams must be >= 1, got " + numParams);
        }
        // Copia superficial: los mapas y listas anidados no se modifican después del parseo.
        hyperparams = Map.copyOf(hyperparams);
    }
}
