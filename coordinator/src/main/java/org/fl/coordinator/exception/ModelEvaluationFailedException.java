package org.fl.coordinator.exception;

import java.util.Map;

/** Falla de {@code eval.py}: código de salida distinto de cero, timeout o salida ilegible. */
public class ModelEvaluationFailedException extends CoordinatorException {

    public ModelEvaluationFailedException(int exitCode, String stderr) {
        super("Model evaluation failed (exit=" + exitCode + "): " + stderr,
            Map.of("exitCode", exitCode, "stderr", stderr == null ? "" : stderr));
    }
}
