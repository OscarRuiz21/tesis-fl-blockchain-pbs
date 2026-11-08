package org.fl.coordinator.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fl.coordinator.model.ClientMetrics;
import org.fl.coordinator.model.ExperimentReport;
import org.fl.coordinator.model.ExperimentRun;
import org.fl.coordinator.model.ExperimentSpec;
import org.fl.coordinator.model.GlobalEvalRecord;
import org.fl.coordinator.model.RoundId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReportGeneratorTest {

    @Test
    void writesValidJsonWithExperimentMetadata(@TempDir Path tmp) throws IOException {
        ReportGenerator gen = new ReportGenerator();

        ExperimentSpec spec = sampleSpec("test-exp", 2, 5);
        ExperimentRun run = new ExperimentRun(
            spec,
            Instant.parse("2026-04-27T10:00:00Z"),
            Instant.parse("2026-04-27T10:30:00Z"),
            List.of(new GlobalEvalRecord(
                RoundId.of(1), 0.75, 0.25,
                Duration.ofSeconds(2),
                Instant.parse("2026-04-27T10:05:00Z"))),
            2, 0);

        List<ClientMetrics> metrics = List.of(
            new ClientMetrics("client-1", 5),
            new ClientMetrics("client-2", 5));

        ExperimentReport report = gen.writeRunSummary(run, metrics, tmp);

        assertThat(Files.exists(report.runSummaryJsonPath())).isTrue();
        assertThat(report.runSummaryJsonPath().getFileName().toString())
            .isEqualTo("run_summary.json");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(report.runSummaryJsonPath().toFile());
        assertThat(json.path("experimentLabel").asText()).isEqualTo("test-exp");
        assertThat(json.path("k").asInt()).isEqualTo(2);
        assertThat(json.path("totalRounds").asInt()).isEqualTo(5);
        assertThat(json.path("globalEvals")).hasSize(1);
        assertThat(json.path("clients")).hasSize(2);
        // Strings opacos preservados sin transformación.
        assertThat(json.path("strategy").asText()).isEqualTo("fedavg");
        assertThat(json.path("anonymityMode").asText()).isEqualTo("pbs");
    }

    @Test
    void calculatesSingleRunMetrics(@TempDir Path tmp) throws IOException {
        ReportGenerator gen = new ReportGenerator();
        ExperimentSpec spec = sampleSpec("metrics-exp", 1, 3);
        Instant t0 = Instant.parse("2026-04-27T10:00:00Z");
        Instant tEnd = t0.plusSeconds(125);

        List<GlobalEvalRecord> evals = List.of(
            new GlobalEvalRecord(RoundId.of(1), 0.55, 0.45, Duration.ofSeconds(1), t0),
            new GlobalEvalRecord(RoundId.of(2), 0.83, 0.17, Duration.ofSeconds(1), tEnd));

        ExperimentRun run = new ExperimentRun(spec, t0, tEnd, evals, 1, 0);
        ExperimentReport report = gen.writeRunSummary(run, List.of(), tmp);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(report.runSummaryJsonPath().toFile());

        assertThat(json.path("totalSeconds").asDouble())
            .isCloseTo(125.0, within(0.01));
        assertThat(json.path("roundsCompleted").asInt()).isEqualTo(2);
        assertThat(json.path("finalAccuracy").asDouble()).isEqualTo(0.83);
        assertThat(json.path("finalLoss").asDouble()).isEqualTo(0.17);
    }

    @Test
    void writesDirectlyInOutputDirWithoutNestedLabelFolder(@TempDir Path tmp) throws IOException {
        // Guarda de regresión: el outputDir ya viene con el label desde el
        // YAML, así que ReportGenerator no debe agregar otro nivel. Los paths
        // del ExperimentReport deben apuntar a archivos efectivamente escritos.
        ReportGenerator gen = new ReportGenerator();
        ExperimentSpec spec = sampleSpec("nested-guard", 1, 1);
        Instant t = Instant.parse("2026-04-27T10:00:00Z");
        ExperimentRun run = new ExperimentRun(
            spec, t, t.plusSeconds(1), List.of(), 1, 0);

        ExperimentReport report = gen.writeRunSummary(run, List.of(), tmp);

        assertThat(report.runSummaryJsonPath()).isEqualTo(tmp.resolve("run_summary.json"));
        assertThat(report.clientCsvsDir()).isEqualTo(tmp);
        assertThat(Files.exists(report.runSummaryJsonPath())).isTrue();
        // Subdir anidado tmp/nested-guard/ NO debe existir.
        assertThat(Files.exists(tmp.resolve("nested-guard"))).isFalse();
    }

    @Test
    void emitsNullFinalMetricsWhenNoEvals(@TempDir Path tmp) throws IOException {
        ReportGenerator gen = new ReportGenerator();
        ExperimentSpec spec = sampleSpec("abort-exp", 1, 1);
        Instant t = Instant.parse("2026-04-27T10:00:00Z");

        ExperimentRun run = new ExperimentRun(
            spec, t, t.plusSeconds(5), List.of(), 0, 1);
        ExperimentReport report = gen.writeRunSummary(run, List.of(), tmp);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(report.runSummaryJsonPath().toFile());
        assertThat(json.path("roundsCompleted").asInt()).isEqualTo(0);
        assertThat(json.path("finalAccuracy").isNull()).isTrue();
        assertThat(json.path("finalLoss").isNull()).isTrue();
    }

    private static ExperimentSpec sampleSpec(String label, int k, int totalRounds) {
        return new ExperimentSpec(
            label, k, totalRounds, 42,
            "fedavg", "pbs",
            "logistic_regression", 10,
            "tabular_csv", Path.of("/d"), Path.of("/t.csv"),
            "iid", null,
            Map.of(),
            Duration.ofSeconds(10),
            Duration.ofSeconds(60),
            Duration.ofSeconds(600),
            Path.of("/r"));
    }
}
