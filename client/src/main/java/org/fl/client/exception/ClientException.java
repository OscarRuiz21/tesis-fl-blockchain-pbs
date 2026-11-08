package org.fl.client.exception;

import java.util.Map;

/** Base de las excepciones del cliente; {@code contextFields} alimenta el log estructurado. */
public abstract class ClientException extends RuntimeException {

    private final Map<String, Object> contextFields;

    protected ClientException(String message, Map<String, Object> contextFields) {
        super(message);
        this.contextFields = Map.copyOf(contextFields);
    }

    protected ClientException(String message, Map<String, Object> contextFields, Throwable cause) {
        super(message, cause);
        this.contextFields = Map.copyOf(contextFields);
    }

    public Map<String, Object> getContextFields() {
        return contextFields;
    }
}
