package org.fl.coordinator.exception;

import java.util.Map;

/** Aborto del experimento: timeout global o un cliente que no llega al estado esperado. */
public class ExperimentAbortedException extends CoordinatorException {

    public ExperimentAbortedException(String reason) {
        super("Experiment aborted: " + reason, Map.of("reason", reason));
    }

    public ExperimentAbortedException(String reason, Throwable cause) {
        super("Experiment aborted: " + reason, Map.of("reason", reason), cause);
    }
}
