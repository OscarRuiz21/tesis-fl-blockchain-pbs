package org.fl.coordinator.exception;

import java.util.Map;

/** Base de las excepciones del coordinator; lleva campos de contexto para el log. */
public abstract class CoordinatorException extends RuntimeException {

    private final Map<String, Object> contextFields;

    protected CoordinatorException(String message, Map<String, Object> contextFields) {
        super(message);
        this.contextFields = Map.copyOf(contextFields);
    }

    protected CoordinatorException(String message, Map<String, Object> contextFields, Throwable cause) {
        super(message, cause);
        this.contextFields = Map.copyOf(contextFields);
    }

    public Map<String, Object> getContextFields() {
        return contextFields;
    }
}
