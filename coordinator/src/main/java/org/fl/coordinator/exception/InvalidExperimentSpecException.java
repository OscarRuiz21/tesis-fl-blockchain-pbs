package org.fl.coordinator.exception;

import java.util.List;
import java.util.Map;

/**
 * Spec inválida. Acumula todos los errores en lugar de detenerse en el
 * primero, para corregir el YAML de una sola vez.
 */
public class InvalidExperimentSpecException extends CoordinatorException {

    public record ValidationError(String field, String message) {
        public ValidationError {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("field must be non-blank");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message must be non-blank");
            }
        }
    }

    private final List<ValidationError> errors;

    public InvalidExperimentSpecException(List<ValidationError> errors) {
        super("Invalid experiment spec: " + errors.size() + " error(s)",
            Map.of("errorCount", errors.size()));
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("errors must be non-empty");
        }
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
