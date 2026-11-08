package org.fl.as.exception;

import java.util.Map;

/**
 * Base de las excepciones del AS; {@code contextFields} lleva los datos del
 * incidente para el log.
 */
public abstract class AuthException extends RuntimeException {

    private final Map<String, Object> contextFields;

    protected AuthException(String message, Map<String, Object> contextFields) {
        super(message);
        this.contextFields = Map.copyOf(contextFields);
    }

    protected AuthException(String message, Map<String, Object> contextFields, Throwable cause) {
        super(message, cause);
        this.contextFields = Map.copyOf(contextFields);
    }

    public Map<String, Object> getContextFields() {
        return contextFields;
    }
}
