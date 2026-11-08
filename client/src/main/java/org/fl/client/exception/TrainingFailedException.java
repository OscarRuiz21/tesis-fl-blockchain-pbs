package org.fl.client.exception;

import java.util.Map;

/** El entrenamiento en Python terminó con código distinto de 0; se conserva su stderr. */
public class TrainingFailedException extends ClientException {

    private final int exitCode;
    private final String stderr;

    public TrainingFailedException(int exitCode, String stderr) {
        super(
            "Training subprocess failed with exit code %d".formatted(exitCode),
            Map.of("exitCode", exitCode, "stderr", stderr == null ? "" : stderr)
        );
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    public int getExitCode() { return exitCode; }
    public String getStderr() { return stderr; }
}
