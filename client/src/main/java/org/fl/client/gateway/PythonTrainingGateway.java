package org.fl.client.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fl.client.exception.TrainingFailedException;
import org.fl.client.model.ClientId;
import org.fl.client.model.GlobalModel;
import org.fl.client.model.ModelUpdate;
import org.fl.client.model.TrainingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entrenamiento local en un subproceso Python. Java no conoce los modelos:
 * pasa una línea de comando fija y los hiperparámetros como JSON opaco, y
 * {@code python/models/<modelName>.py} decide el resto.
 */
@Component
public class PythonTrainingGateway {

    private static final Logger log = LoggerFactory.getLogger(PythonTrainingGateway.class);

    private final ProcessSupervisor supervisor;
    private final IPCProtocol ipc;
    private final StdoutMetricsParser metricsParser;
    private final ObjectMapper jackson = new ObjectMapper();

    private final String pythonExecutable;
    private final String pythonScriptPath;
    private final Duration timeout;

    public PythonTrainingGateway(
            ProcessSupervisor supervisor,
            IPCProtocol ipc,
            StdoutMetricsParser metricsParser,
            @Value("${fl.python.executable}") String pythonExecutable,
            @Value("${fl.python.script-path}") String pythonScriptPath,
            @Value("${fl.python.timeout-minutes}") long timeoutMinutes) {
        this.supervisor = supervisor;
        this.ipc = ipc;
        this.metricsParser = metricsParser;
        this.pythonExecutable = pythonExecutable;
        this.pythonScriptPath = pythonScriptPath;
        this.timeout = Duration.ofMinutes(timeoutMinutes);
    }

    public TrainingResult train(
            String modelName,
            int numParams,
            GlobalModel base,
            Path datasetPath,
            ClientId clientId,
            Map<String, Object> hyperparams) {

        Path modelIn;
        Path updateOut;
        try {
            modelIn = Files.createTempFile("model_in_", ".bin");
            updateOut = Files.createTempFile("update_out_", ".bin");
        } catch (IOException e) {
            throw new TrainingFailedException(-1, "Failed to create temp files: " + e.getMessage());
        }

        try {
            ipc.writeBinary(modelIn, base.bytes());

            String hyperparamsJson;
            try {
                hyperparamsJson = jackson.writeValueAsString(hyperparams);
            } catch (JsonProcessingException e) {
                throw new TrainingFailedException(-1,
                    "Failed to serialize hyperparams: " + e.getMessage());
            }

            List<String> command = List.of(
                pythonExecutable, pythonScriptPath,
                "--model_name", modelName,
                "--num_params", String.valueOf(numParams),
                "--model_in", modelIn.toString(),
                "--update_out", updateOut.toString(),
                "--data", datasetPath.toString(),
                "--client_id", clientId.value(),
                "--hyperparams", hyperparamsJson
            );

            // El abuelo del script (/opt para /opt/python/train.py) permite
            // `from python.models import ...`.
            Path scriptParent = Path.of(pythonScriptPath).getParent();
            String pythonpath = scriptParent != null && scriptParent.getParent() != null
                ? scriptParent.getParent().toString()
                : "";
            Map<String, String> env = new HashMap<>();
            env.put("PYTHONPATH", pythonpath);

            ProcessSupervisor.ProcessResult result = supervisor.run(timeout, command, env);

            if (result.exitCode() != 0) {
                throw new TrainingFailedException(result.exitCode(), result.stderr());
            }

            byte[] updateBytes = ipc.readBinary(updateOut);
            StdoutMetricsParser.ParsedMetrics parsed = metricsParser.parse(result.stdout());

            return new TrainingResult(
                ModelUpdate.of(updateBytes),
                parsed.loss(),
                parsed.accuracy(),
                parsed.numSamples(),
                Duration.ofMillis(result.durationMs())
            );
        } catch (TrainingFailedException tfe) {
            throw tfe;
        } catch (IOException e) {
            throw new TrainingFailedException(-1, "IO error during training: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TrainingFailedException(-1, "Training interrupted");
        } finally {
            try {
                Files.deleteIfExists(modelIn);
                Files.deleteIfExists(updateOut);
            } catch (IOException e) {
                log.warn("Failed to delete temp files", e);
            }
        }
    }
}
