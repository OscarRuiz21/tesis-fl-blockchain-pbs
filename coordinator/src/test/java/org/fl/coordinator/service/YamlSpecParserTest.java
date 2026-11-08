package org.fl.coordinator.service;

import org.fl.coordinator.model.ExperimentSpec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlSpecParserTest {

    private final YamlSpecParser parser = new YamlSpecParser();

    @Test
    void parsesValidYamlWithAllFields() {
        String yaml = """
            experiment:
              label: "uci-K5-R10"
              seed: 42
            model:
              name: "logistic_regression"
              num_params: 26
              hyperparams:
                epochs: 5
                lr: 0.01
                batch_size: 32
            dataset:
              type: "tabular_csv"
              base_path: "/data/partitions"
              test_set_path: "/data/test/uci_credit_test.csv"
            partition:
              strategy: "iid"
            federation:
              num_clients: 5
              num_rounds: 10
              aggregation: "fedavg"
              anonymity: "pbs"
            timeouts:
              t_train_seconds: 60
              t_round_seconds: 180
              t_experiment_seconds: 3600
            output:
              results_dir: "/results/uci-K5-R10"
            """;

        ExperimentSpec spec = parser.parse(yaml);

        assertThat(spec.label()).isEqualTo("uci-K5-R10");
        assertThat(spec.seed()).isEqualTo(42);
        assertThat(spec.modelName()).isEqualTo("logistic_regression");
        assertThat(spec.numParams()).isEqualTo(26);
        assertThat(spec.datasetType()).isEqualTo("tabular_csv");
        assertThat(spec.datasetBasePath().toString()).isEqualTo("/data/partitions");
        assertThat(spec.testSetPath().toString()).isEqualTo("/data/test/uci_credit_test.csv");
        assertThat(spec.partitionStrategy()).isEqualTo("iid");
        assertThat(spec.dirichletAlpha()).isNull();
        assertThat(spec.k()).isEqualTo(5);
        assertThat(spec.totalRounds()).isEqualTo(10);
        assertThat(spec.aggregationStrategy()).isEqualTo("fedavg");
        assertThat(spec.anonymityMode()).isEqualTo("pbs");
        assertThat(spec.tTrainSeconds()).isEqualTo(Duration.ofSeconds(60));
        assertThat(spec.tRoundSeconds()).isEqualTo(Duration.ofSeconds(180));
        assertThat(spec.tExperimentSeconds()).isEqualTo(Duration.ofSeconds(3600));
        assertThat(spec.resultsDir().toString()).isEqualTo("/results/uci-K5-R10");
        assertThat(spec.hyperparams())
            .containsEntry("epochs", 5)
            .containsEntry("lr", 0.01)
            .containsEntry("batch_size", 32);
    }

    @Test
    void parsesDirichletAlphaWhenStrategyIsDirichlet() {
        String yaml = """
            experiment:
              label: "non-iid"
              seed: 1
            model:
              name: "logistic_regression"
              num_params: 10
              hyperparams: {}
            dataset:
              type: "tabular_csv"
              base_path: "/d"
              test_set_path: "/t.csv"
            partition:
              strategy: "dirichlet"
              alpha: 0.3
            federation:
              num_clients: 2
              num_rounds: 1
              aggregation: "fedavg"
              anonymity: "none"
            timeouts:
              t_train_seconds: 1
              t_round_seconds: 1
              t_experiment_seconds: 1
            output:
              results_dir: "/r"
            """;

        ExperimentSpec spec = parser.parse(yaml);

        assertThat(spec.partitionStrategy()).isEqualTo("dirichlet");
        assertThat(spec.dirichletAlpha()).isEqualTo(0.3);
    }

    @Test
    void failsLoudOnMissingExperimentBlock() {
        String yaml = """
            model:
              name: "x"
              num_params: 1
            """;

        assertThatThrownBy(() -> parser.parse(yaml))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("experiment");
    }

    @Test
    void parsesHyperparamsWithNestedListsAndMaps() {
        String yaml = """
            experiment:
              label: "x"
              seed: 1
            model:
              name: "logistic_regression"
              num_params: 3
              hyperparams:
                features:
                  - "age"
                  - "income"
                target: "default"
                nested:
                  lr: 0.01
                  scheduler: "cosine"
            dataset:
              type: "tabular_csv"
              base_path: "/d"
              test_set_path: "/t.csv"
            partition:
              strategy: "iid"
            federation:
              num_clients: 1
              num_rounds: 1
              aggregation: "fedavg"
              anonymity: "pbs"
            timeouts:
              t_train_seconds: 1
              t_round_seconds: 1
              t_experiment_seconds: 1
            output:
              results_dir: "/r"
            """;

        ExperimentSpec spec = parser.parse(yaml);

        Map<String, Object> hp = spec.hyperparams();
        assertThat(hp).containsKey("features");
        @SuppressWarnings("unchecked")
        List<String> features = (List<String>) hp.get("features");
        assertThat(features).containsExactly("age", "income");
        assertThat(hp.get("target")).isEqualTo("default");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) hp.get("nested");
        assertThat(nested)
            .containsEntry("lr", 0.01)
            .containsEntry("scheduler", "cosine");
    }
}
