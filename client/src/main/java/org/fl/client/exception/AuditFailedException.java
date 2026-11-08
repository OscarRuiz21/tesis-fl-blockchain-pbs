package org.fl.client.exception;

import java.util.Map;

/** Discrepancia en la auditoría P4/P5; solo se lanza con {@code fl.audit.strict=true}. */
public class AuditFailedException extends ClientException {

    public AuditFailedException(String message, Map<String, Object> contextFields) {
        super(message, contextFields);
    }
}
