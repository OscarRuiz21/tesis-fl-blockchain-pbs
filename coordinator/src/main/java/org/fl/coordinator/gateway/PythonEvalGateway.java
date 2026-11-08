package org.fl.coordinator.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.fl.coordinator.exception.ModelEvaluationFailedException;
import org.fl.coordinator.model.EvaluationResult;
import org.fl.coordinator.model.GlobalModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Evalúa el modelo global con {@code eval.py} sobre el conjunto de prueba
 * centralizado. El modelo pasa por un archivo temporal y los hiperparámetros
 * como JSON en {@code --hyperparams}.
 */
@Component
@RequiredArgsConstructor
public class PythonEvalGateway {

    private static final Logger log = LoggerFactory.getLogger(PythonEvalGateway.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final EvalStdoutParser parser;
    private final ObjectMapper jackson = new ObjectMapper();

    @Value("${fl.python.executable}")
    private String pythonExecutable;

    @Value("${fl.python.eval-script}")
    private String evalScript;

    public EvaluationResult evaluate(GlobalModel model, EvalParams params) {
        Path modelFile;
        try {
            modelFile = Files.createTempFile("eval_model_", ".bin");
            Files.write(modelFile, model.bytes());
        } catch (IOException e) {
            throw new ModelEvaluationFailedException(
                -1, "Failed to write model file: " + e.getMessage());
        }
        Instant t0 = Instant.now();
        try {
            String hyperparamsJson = serializeHyperparams(params.hyperparams());
            List<String> cmd = new ArrayList<>(List.of(
                pythonExecutable,
                evalScript,
                "--model_name", params.modelName(),
                "--num_params", String.valueOf(params.numParams()),
                "--model_in", modelFile.toString(),
                "--testset", params.testSetPath().toString(),
                "--hyperparams", hyperparamsJson
            ));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            // /opt/python/eval.py da PYTHONPATH=/opt, para que resuelvan los
            // imports `from python.models...`.
            Path scriptPath = Path.of(evalScript);
            pb.environment().put("PYTHONPATH", scriptPath.getParent().getParent().toString());
            Process p = pb.start();

            boolean finished = p.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Duration evalTime = Duration.between(t0, Instant.now());

            if (!finished) {
                p.destroyForcibly();
                throw new ModelEvaluationFailedException(-1, "Eval subprocess timeout");
            }
            String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            if (p.exitValue() != 0) {
                throw new ModelEvaluationFailedException(p.exitValue(), stderr);
            }
            return parser.parse(stdout, evalTime);
        } catch (IOException e) {
            throw new ModelEvaluationFailedException(-1, "IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelEvaluationFailedException(-1, "Interrupted");
        } finally {
            try {
                Files.deleteIfExists(modelFile);
            } catch (IOException e) {
                log.warn("Failed to delete temp model file", e);
            }
        }
    }

    private String serializeHyperparams(Map<String, Object> hyperparams) {
        try {
            return jackson.writeValueAsString(hyperparams);
        } catch (JsonProcessingException e) {
            throw new ModelEvaluationFailedException(-1,
                "Failed to serialize hyperparams: " + e.getMessage());
        }
    }

    public record EvalParams(
        String modelName,
        int numParams,
        Path testSetPath,
        Map<String, Object> hyperparams
    ) {
        public EvalParams {
            if (modelName == null || modelName.isBlank()) {
                throw new IllegalArgumentException("modelName must be non-blank");
            }
            if (numParams < 1) {
                throw new IllegalArgumentException("numParams must be >= 1");
            }
            if (testSetPath == null) {
                throw new IllegalArgumentException("testSetPath must not be null");
            }
            if (hyperparams == null) {
                throw new IllegalArgumentException("hyperparams must not be null");
            }
            hyperparams = Map.copyOf(hyperparams);
        }
    }
}
