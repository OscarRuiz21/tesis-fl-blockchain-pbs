package org.fl.coordinator.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.fl.coordinator.model.ClientMetrics;
import org.fl.coordinator.model.ExperimentReport;
import org.fl.coordinator.model.ExperimentRun;
import org.fl.coordinator.model.GlobalEvalRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Escribe {@code run_summary.json}: parámetros de la corrida, métricas finales y evaluación por ronda. */
@Component
public class ReportGenerator {

    public static final String RUN_SUMMARY_FILE = "run_summary.json";

    private final ObjectMapper jackson = new ObjectMapper()
        .findAndRegisterModules()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public ExperimentReport writeRunSummary(
            ExperimentRun run,
            List<ClientMetrics> clientMetrics,
            Path outputDir) {
        // outputDir (output.results_dir del YAML) ya incluye el label; volver a
        // agregarlo daría results/<label>/<label>/ y rutas inexistentes en el reporte.
        Path runDir = outputDir;
        try {
            Files.createDirectories(runDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("experimentLabel", run.spec().label());
        body.put("k", run.spec().k());
        body.put("totalRounds", run.spec().totalRounds());
        body.put("strategy", run.spec().aggregationStrategy());
        body.put("anonymityMode", run.spec().anonymityMode());
        body.put("seed", run.spec().seed());
        body.put("startedAt", run.startedAt().toString());
        body.put("finishedAt", run.finishedAt().toString());
        body.put("clientsCompleted", run.clientsCompleted());
        body.put("clientsFailed", run.clientsFailed());

        double totalSeconds =
            Duration.between(run.startedAt(), run.finishedAt()).toMillis() / 1000.0;
        body.put("totalSeconds", totalSeconds);
        body.put("roundsCompleted", run.globalEvals().size());

        if (!run.globalEvals().isEmpty()) {
            GlobalEvalRecord last = run.globalEvals().get(run.globalEvals().size() - 1);
            body.put("finalAccuracy", last.accuracy());
            body.put("finalLoss", last.loss());
        } else {
            body.put("finalAccuracy", null);
            body.put("finalLoss", null);
        }

        List<Map<String, Object>> evals = run.globalEvals().stream()
            .map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("round", r.roundId().value());
                m.put("accuracy", r.accuracy());
                m.put("loss", r.loss());
                m.put("evalTimeMs", r.evaluationTime().toMillis());
                m.put("timestamp", r.timestamp().toString());
                return m;
            })
            .toList();
        body.put("globalEvals", evals);

        List<Map<String, Object>> clients = clientMetrics.stream()
            .map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("clientId", c.clientId());
                m.put("recordedRounds", c.recordedRounds());
                return m;
            })
            .toList();
        body.put("clients", clients);

        Path summaryPath = runDir.resolve(RUN_SUMMARY_FILE);
        try {
            jackson.writeValue(summaryPath.toFile(), body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new ExperimentReport(
            run.spec().label(),
            summaryPath,
            runDir.resolve("global_eval.csv"),
            runDir,
            run.spec().k(),
            run.spec().totalRounds());
    }
}
