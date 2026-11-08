package org.fl.coordinator.service;

import org.fl.coordinator.exception.InvalidExperimentSpecException;
import org.fl.coordinator.exception.InvalidExperimentSpecException.ValidationError;
import org.fl.coordinator.model.ExperimentSpec;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Valida la spec antes de tocar el contrato, el AS o los clientes, y reporta
 * todos los errores juntos.
 */
@Service
public class SpecValidator {

    // Debe coincidir con MODEL_REGISTRY en Python; nada lo verifica automáticamente.
    private static final Set<String> ALLOWED_MODELS = Set.of("logistic_regression", "mlp");

    private static final Set<String> ALLOWED_AGGREGATIONS = Set.of("fedavg", "fedmedian", "fedprox");

    private static final Set<String> ALLOWED_ANONYMITY_MODES = Set.of("pbs", "none");

    private static final Set<String> ALLOWED_PARTITION_STRATEGIES = Set.of("iid", "dirichlet");

    public static Set<String> allowedModels() { return ALLOWED_MODELS; }
    public static Set<String> allowedAggregations() { return ALLOWED_AGGREGATIONS; }
    public static Set<String> allowedAnonymityModes() { return ALLOWED_ANONYMITY_MODES; }
    public static Set<String> allowedPartitionStrategies() { return ALLOWED_PARTITION_STRATEGIES; }

    public void validate(ExperimentSpec spec) {
        List<ValidationError> errors = new ArrayList<>();

        if (!ALLOWED_MODELS.contains(spec.modelName())) {
            errors.add(new ValidationError("modelName",
                "not in MODEL_REGISTRY whitelist: " + spec.modelName()
                + " (allowed: " + ALLOWED_MODELS + ")"));
        }
        if (!ALLOWED_AGGREGATIONS.contains(spec.aggregationStrategy())) {
            errors.add(new ValidationError("aggregationStrategy",
                "unknown aggregation: " + spec.aggregationStrategy()
                + " (allowed: " + ALLOWED_AGGREGATIONS + ")"));
        }
        if (!ALLOWED_ANONYMITY_MODES.contains(spec.anonymityMode())) {
            errors.add(new ValidationError("anonymityMode",
                "unknown anonymity mode: " + spec.anonymityMode()
                + " (allowed: " + ALLOWED_ANONYMITY_MODES + ")"));
        }
        if (!ALLOWED_PARTITION_STRATEGIES.contains(spec.partitionStrategy())) {
            errors.add(new ValidationError("partitionStrategy",
                "unknown partition strategy: " + spec.partitionStrategy()
                + " (allowed: " + ALLOWED_PARTITION_STRATEGIES + ")"));
        }

        if (spec.k() < 1) {
            errors.add(new ValidationError("k", "must be >= 1, got " + spec.k()));
        }
        if (spec.totalRounds() < 1) {
            errors.add(new ValidationError("totalRounds",
                "must be >= 1, got " + spec.totalRounds()));
        }
        if (spec.numParams() < 1) {
            errors.add(new ValidationError("numParams",
                "must be >= 1, got " + spec.numParams()));
        }

        if ("dirichlet".equals(spec.partitionStrategy())) {
            if (spec.dirichletAlpha() == null) {
                errors.add(new ValidationError("dirichletAlpha",
                    "partition.alpha is required when strategy=dirichlet"));
            } else if (spec.dirichletAlpha() <= 0) {
                errors.add(new ValidationError("dirichletAlpha",
                    "partition.alpha must be > 0, got " + spec.dirichletAlpha()));
            }
        }

        // Solo se verifica el conjunto de prueba; las particiones de basePath
        // las genera prepare-data.sh antes de la corrida y no se revisan aquí.
        if (!Files.exists(spec.testSetPath())) {
            errors.add(new ValidationError("testSetPath",
                "test set file does not exist: " + spec.testSetPath()));
        }

        validatePositiveDuration("tTrainSeconds", spec.tTrainSeconds(), errors);
        validatePositiveDuration("tRoundSeconds", spec.tRoundSeconds(), errors);
        validatePositiveDuration("tExperimentSeconds", spec.tExperimentSeconds(), errors);

        if (!errors.isEmpty()) {
            throw new InvalidExperimentSpecException(errors);
        }
    }

    private static void validatePositiveDuration(
            String field, Duration value, List<ValidationError> errors) {
        if (value == null || value.isZero() || value.isNegative()) {
            errors.add(new ValidationError(field, "must be > 0, got " + value));
        }
    }
}
