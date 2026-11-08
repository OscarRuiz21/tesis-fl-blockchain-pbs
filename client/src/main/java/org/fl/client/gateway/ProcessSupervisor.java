package org.fl.client.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Ejecuta un subproceso con límite de tiempo. Al vencer se mata con
 * {@code destroyForcibly} y se esperan 2 s a que el SO lo recoja, para no
 * dejar zombis; el resultado es {@code exitCode=-1, stderr="TIMEOUT"}.
 */
@Component
public class ProcessSupervisor {

    private static final Logger log = LoggerFactory.getLogger(ProcessSupervisor.class);

    public ProcessResult run(Duration timeout, List<String> command, Map<String, String> env)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        pb.environment().putAll(env);

        long t0 = System.nanoTime();
        Process process = pb.start();

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        if (!finished) {
            log.warn("Process timed out after {}ms; destroying forcibly: {}", elapsedMs, command);
            process.destroyForcibly();
            try {
                process.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return new ProcessResult(-1, "", "TIMEOUT", elapsedMs);
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.exitValue(), stdout, stderr, elapsedMs);
    }

    public record ProcessResult(int exitCode, String stdout, String stderr, long durationMs) {}
}
