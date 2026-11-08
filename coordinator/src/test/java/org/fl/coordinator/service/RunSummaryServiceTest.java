package org.fl.coordinator.service;

import org.fl.coordinator.model.ClientMetrics;
import org.fl.coordinator.model.ExperimentReport;
import org.fl.coordinator.model.ExperimentRun;
import org.fl.coordinator.model.ExperimentSpec;
import org.fl.coordinator.store.FileMetricsReader;
import org.fl.coordinator.store.ReportGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunSummaryServiceTest {

    @Test
    void delegatesToReaderAndReportGen() {
        FileMetricsReader reader = mock(FileMetricsReader.class);
        ReportGenerator reportGen = mock(ReportGenerator.class);

        ExperimentSpec spec = sampleSpec(3);
        ExperimentRun run = new ExperimentRun(
            spec, Instant.now(), Instant.now(), List.of(), 3, 0);
        Path outDir = Path.of("/tmp/out");

        List<ClientMetrics> metrics = List.of(new ClientMetrics("client-1", 2));
        when(reader.readAll(eq("exp"), eq(3))).thenReturn(metrics);
        ExperimentReport expected = new ExperimentReport(
            "exp",
            outDir.resolve("exp/run_summary.json"),
            outDir.resolve("exp/global_eval.csv"),
            outDir.resolve("exp"),
            3, 2);
        when(reportGen.writeRunSummary(eq(run), any(), eq(outDir))).thenReturn(expected);

        RunSummaryService service = new RunSummaryService(reader, reportGen);
        ExperimentReport actual = service.generateRunSummary(run, outDir);

        assertThat(actual).isEqualTo(expected);
        verify(reader).readAll("exp", 3);
        verify(reportGen).writeRunSummary(eq(run), any(), eq(outDir));
    }

    @Test
    void passesSpecKToReader() {
        // El service no asume nada sobre cuántos clientes hay: reenvía spec.k()
        // tal cual al reader. K=10 debe llegar tal cual.
        FileMetricsReader reader = mock(FileMetricsReader.class);
        ReportGenerator reportGen = mock(ReportGenerator.class);

        ExperimentSpec spec = sampleSpec(10);
        ExperimentRun run = new ExperimentRun(
            spec, Instant.now(), Instant.now(), List.of(), 10, 0);

        when(reader.readAll(eq("exp"), eq(10))).thenReturn(List.of());
        when(reportGen.writeRunSummary(any(), any(), any())).thenReturn(
            new ExperimentReport("exp",
                Path.of("/o/exp/run_summary.json"),
                Path.of("/o/exp/global_eval.csv"),
                Path.of("/o/exp"), 10, 2));

        new RunSummaryService(reader, reportGen).generateRunSummary(run, Path.of("/o"));

        verify(reader).readAll("exp", 10);
    }

    private static ExperimentSpec sampleSpec(int k) {
        return new ExperimentSpec(
            "exp", k, 2, 1,
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
