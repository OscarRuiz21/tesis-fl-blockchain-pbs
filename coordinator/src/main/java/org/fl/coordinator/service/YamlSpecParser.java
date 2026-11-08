package org.fl.coordinator.service;

import org.fl.coordinator.model.ExperimentSpec;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Convierte el YAML de un experimento en {@link ExperimentSpec}. SnakeYAML va
 * con {@link SafeConstructor}, que solo construye tipos básicos y no clases
 * arbitrarias. Aquí solo se revisan presencia y tipos; la semántica la valida
 * {@code SpecValidator}.
 *
 * <p>Estructura (ver {@code experiments/uci_B1_K5_R5_fedavg_pbs.yml}):</p>
 *
 * <pre>
 * experiment:
 *   label: "uci-B1-K5-R5-fedavg-pbs"
 *   seed: 42
 *
 * model:
 *   name: "logistic_regression"
 *   num_params: 24
 *   hyperparams:           # se reenvía sin interpretar al cliente y a eval.py
 *     epochs: 3
 *     lr: 0.05
 *     batch_size: 64
 *     target: "default_next_month"
 *     features: [limit_bal, sex, ...]
 *
 * dataset:
 *   type: "tabular_csv"
 *   name: "uci_credit"
 *   base_path: "/opt/python/data/uci_credit/partitions/K5_seed42_iid"
 *   test_set_path: "/opt/python/data/uci_credit/test.csv"
 *
 * partition:
 *   strategy: "iid"        # iid | dirichlet
 *   alpha: 0.5             # solo con dirichlet
 *
 * federation:
 *   num_clients: 5
 *   num_rounds: 5
 *   aggregation: "fedavg"  # fedavg | fedmedian | fedprox
 *   anonymity: "pbs"       # pbs | none
 *
 * timeouts:
 *   t_train_seconds: 120
 *   t_round_seconds: 240
 *   t_experiment_seconds: 1800
 *
 * output:
 *   results_dir: "/opt/results/uci-B1-K5-R5-fedavg-pbs"
 * </pre>
 */
@Service
public class YamlSpecParser {

    private final Yaml yaml;

    public YamlSpecParser() {
        LoaderOptions opts = new LoaderOptions();
        opts.setMaxAliasesForCollections(50);
        this.yaml = new Yaml(new SafeConstructor(opts));
    }

    /** @throws IllegalArgumentException si falta un campo o tiene otro tipo */
    public ExperimentSpec parse(String yamlBody) {
        Map<String, Object> root;
        try {
            root = yaml.load(yamlBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML parse error: " + e.getMessage(), e);
        }
        if (root == null) {
            throw new IllegalArgumentException("YAML body is empty");
        }

        Map<String, Object> experiment = requireMap(root, "experiment");
        Map<String, Object> model = requireMap(root, "model");
        Map<String, Object> dataset = requireMap(root, "dataset");
        Map<String, Object> partition = requireMap(root, "partition");
        Map<String, Object> federation = requireMap(root, "federation");
        Map<String, Object> timeouts = requireMap(root, "timeouts");
        Map<String, Object> output = requireMap(root, "output");

        String label = requireString(experiment, "experiment.label");
        int seed = requireInt(experiment, "experiment.seed");

        String modelName = requireString(model, "model.name");
        int numParams = requireInt(model, "model.num_params");

        @SuppressWarnings("unchecked")
        Map<String, Object> hyperparams = (Map<String, Object>) model.getOrDefault(
            "hyperparams", new HashMap<String, Object>());

        String datasetType = requireString(dataset, "dataset.type");
        Path basePath = Paths.get(requireString(dataset, "dataset.base_path"));
        Path testSetPath = Paths.get(requireString(dataset, "dataset.test_set_path"));

        String partitionStrategy = requireString(partition, "partition.strategy");
        Double dirichletAlpha = optionalDouble(partition, "alpha");

        int k = requireInt(federation, "federation.num_clients");
        int totalRounds = requireInt(federation, "federation.num_rounds");
        String aggregation = requireString(federation, "federation.aggregation");
        String anonymity = requireString(federation, "federation.anonymity");

        Duration tTrain = Duration.ofSeconds(requireLong(timeouts, "timeouts.t_train_seconds"));
        Duration tRound = Duration.ofSeconds(requireLong(timeouts, "timeouts.t_round_seconds"));
        Duration tExperiment = Duration.ofSeconds(requireLong(timeouts, "timeouts.t_experiment_seconds"));

        Path resultsDir = Paths.get(requireString(output, "output.results_dir"));

        return new ExperimentSpec(
            label, k, totalRounds, seed,
            aggregation, anonymity,
            modelName, numParams,
            datasetType, basePath, testSetPath,
            partitionStrategy, dirichletAlpha,
            hyperparams,
            tTrain, tRound, tExperiment,
            resultsDir);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required block: " + key);
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Field " + key + " must be a map, got: "
                + value.getClass().getSimpleName());
        }
        return (Map<String, Object>) value;
    }

    private static String requireString(Map<String, Object> parent, String fieldName) {
        String[] parts = fieldName.split("\\.");
        String key = parts[parts.length - 1];
        Object value = parent.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException("Field " + fieldName + " must be a string, got: "
                + value.getClass().getSimpleName());
        }
        return s;
    }

    private static int requireInt(Map<String, Object> parent, String fieldName) {
        return (int) requireLong(parent, fieldName);
    }

    private static long requireLong(Map<String, Object> parent, String fieldName) {
        String[] parts = fieldName.split("\\.");
        String key = parts[parts.length - 1];
        Object value = parent.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalArgumentException("Field " + fieldName + " must be a number, got: "
            + value.getClass().getSimpleName());
    }

    private static Double optionalDouble(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) return null;
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Field " + key + " must be a number, got: "
            + value.getClass().getSimpleName());
    }
}
