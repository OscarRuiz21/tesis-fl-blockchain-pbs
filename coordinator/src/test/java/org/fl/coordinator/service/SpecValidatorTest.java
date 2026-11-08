package org.fl.coordinator.service;

import org.fl.coordinator.exception.InvalidExperimentSpecException;
import org.fl.coordinator.exception.InvalidExperimentSpecException.ValidationError;
import org.fl.coordinator.model.ExperimentSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecValidatorTest {

    private final SpecValidator validator = new SpecValidator();

    @Test
    void acceptsValidMvpSpec(@TempDir Path tmp) throws IOException {
        Path testSet = Files.createFile(tmp.resolve("test.csv"));
        ExperimentSpec spec = validSpec(testSet);

        assertThatCode(() -> validator.validate(spec)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownModel(@TempDir Path tmp) throws IOException {
        Path testSet = Files.createFile(tmp.resolve("test.csv"));
        ExperimentSpec spec = withModel(validSpec(testSet), "fancy_transformer");

        assertThatThrownBy(() -> validator.validate(spec))
            .isInstanceOf(InvalidExperimentSpecException.class)
            .satisfies(e -> {
                InvalidExperimentSpecException ex = (InvalidExperimentSpecException) e;
                assertThat(ex.getErrors())
                    .extracting(ValidationError::field)
                    .contains("modelName");
            });
    }

    @Test
    void rejectsUnknownAggregation(@TempDir Path tmp) throws IOException {
        Path testSet = Files.createFile(tmp.resolve("test.csv"));
        ExperimentSpec spec = withAggregation(validSpec(testSet), "fedwhatever");

        assertThatThrownBy(() -> validator.validate(spec))
            .isInstanceOf(InvalidExperimentSpecException.class);
    }

    @Test
    void rejectsDirichletWithoutAlpha(@TempDir Path tmp) throws IOException {
        Path testSet = Files.createFile(tmp.resolve("test.csv"));
        ExperimentSpec spec = withPartition(validSpec(testSet), "dirichlet", null);

        InvalidExperimentSpecException ex = catchValidation(spec);
        assertThat(ex.getErrors())
            .extracting(ValidationError::field)
            .contains("dirichletAlpha");
    }

    @Test
    void rejectsDirichletWithNonPositiveAlpha(@TempDir Path tmp) throws IOException {
        Path testSet = Files.createFile(tmp.resolve("test.csv"));
        ExperimentSpec spec = withPartition(validSpec(testSet), "dirichlet", 0.0);

        InvalidExperimentSpecException ex = catchValidation(spec);
        assertThat(ex.getErrors())
            .extracting(ValidationError::field)
            .contains("dirichletAlpha");
    }

    @Test
    void rejectsMissingTestSetFile(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.csv");
        ExperimentSpec spec = validSpec(missing);

        InvalidExperimentSpecException ex = catchValidation(spec);
        assertThat(ex.getErrors())
            .extracting(ValidationError::field)
            .contains("testSetPath");
    }

    @Test
    void accumulatesMultipleErrorsInSingleException(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.csv");
        // model + aggregation + testSet: 3 errores a la vez.
        ExperimentSpec spec = withAggregation(
            withModel(validSpec(missing), "alien_model"),
            "fedalien");

        InvalidExperimentSpecException ex = catchValidation(spec);
        assertThat(ex.getErrors()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(ex.getErrors())
            .extracting(ValidationError::field)
            .containsExactlyInAnyOrder("modelName", "aggregationStrategy", "testSetPath");
    }

    // ---------- Helpers ----------

    private static ExperimentSpec validSpec(Path testSet) {
        return new ExperimentSpec(
            "test-exp", 2, 1, 42,
            "fedavg", "pbs",
            "logistic_regression", 26,
            "tabular_csv",
            Path.of("/tmp/partitions"),
            testSet,
            "iid", null,
            Map.of("epochs", 1),
            Duration.ofSeconds(10),
            Duration.ofSeconds(60),
            Duration.ofSeconds(600),
            Path.of("/tmp/results"));
    }

    private static ExperimentSpec withModel(ExperimentSpec s, String modelName) {
        return new ExperimentSpec(s.label(), s.k(), s.totalRounds(), s.seed(),
            s.aggregationStrategy(), s.anonymityMode(),
            modelName, s.numParams(),
            s.datasetType(), s.datasetBasePath(), s.testSetPath(),
            s.partitionStrategy(), s.dirichletAlpha(),
            s.hyperparams(),
            s.tTrainSeconds(), s.tRoundSeconds(), s.tExperimentSeconds(),
            s.resultsDir());
    }

    private static ExperimentSpec withAggregation(ExperimentSpec s, String agg) {
        return new ExperimentSpec(s.label(), s.k(), s.totalRounds(), s.seed(),
            agg, s.anonymityMode(),
            s.modelName(), s.numParams(),
            s.datasetType(), s.datasetBasePath(), s.testSetPath(),
            s.partitionStrategy(), s.dirichletAlpha(),
            s.hyperparams(),
            s.tTrainSeconds(), s.tRoundSeconds(), s.tExperimentSeconds(),
            s.resultsDir());
    }

    private static ExperimentSpec withPartition(ExperimentSpec s, String strategy, Double alpha) {
        return new ExperimentSpec(s.label(), s.k(), s.totalRounds(), s.seed(),
            s.aggregationStrategy(), s.anonymityMode(),
            s.modelName(), s.numParams(),
            s.datasetType(), s.datasetBasePath(), s.testSetPath(),
            strategy, alpha,
            s.hyperparams(),
            s.tTrainSeconds(), s.tRoundSeconds(), s.tExperimentSeconds(),
            s.resultsDir());
    }

    private static InvalidExperimentSpecException catchValidation(ExperimentSpec spec) {
        try {
            new SpecValidator().validate(spec);
        } catch (InvalidExperimentSpecException e) {
            return e;
        }
        throw new AssertionError("expected InvalidExperimentSpecException");
    }
}
