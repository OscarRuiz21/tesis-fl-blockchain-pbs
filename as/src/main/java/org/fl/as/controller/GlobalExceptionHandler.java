package org.fl.as.controller;

import org.fl.as.exception.AdminAccessDeniedException;
import org.fl.as.exception.AuthException;
import org.fl.as.exception.ClientIdMismatchException;
import org.fl.as.exception.ClientNotAuthenticatedException;
import org.fl.as.exception.ClientNotEligibleException;
import org.fl.as.exception.DuplicateCredentialRequestException;
import org.fl.as.exception.RoundNotActiveException;
import org.fl.as.exception.SessionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Traduce las excepciones de dominio del AS a respuestas HTTP con cuerpo
 * {@code {"code","message"}}. Los detalles sensibles (CN ofensor, identidad
 * declarada) van solo al log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(String code, String message) {}

    @ExceptionHandler(ClientNotEligibleException.class)
    public ResponseEntity<ErrorBody> handleNotEligible(ClientNotEligibleException ex) {
        log.warn("Client not eligible: {}", ex.getClientId().value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorBody("client_not_eligible", ex.getMessage()));
    }

    @ExceptionHandler(ClientNotAuthenticatedException.class)
    public ResponseEntity<ErrorBody> handleNotAuthenticated(ClientNotAuthenticatedException ex) {
        log.warn("Client not authenticated: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorBody("client_not_authenticated", ex.getMessage()));
    }

    @ExceptionHandler(ClientIdMismatchException.class)
    public ResponseEntity<ErrorBody> handleClientIdMismatch(ClientIdMismatchException ex) {
        log.warn("Client ID mismatch, declared='{}' certCn='{}'",
            ex.getDeclaredClientId(), ex.getCertCn());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorBody("client_id_mismatch",
                "Cert identity does not match declared clientId"));
    }

    @ExceptionHandler(DuplicateCredentialRequestException.class)
    public ResponseEntity<ErrorBody> handleDuplicate(DuplicateCredentialRequestException ex) {
        log.warn("Duplicate credential request: client={} round={}",
            ex.getClientId().value(), ex.getRound().value());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorBody("duplicate_request", ex.getMessage()));
    }

    @ExceptionHandler(RoundNotActiveException.class)
    public ResponseEntity<ErrorBody> handleRoundNotActive(RoundNotActiveException ex) {
        log.warn("Round not active, requested={} active={}",
            ex.getRequestedRound().value(), ex.getActiveRound());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorBody("round_not_active", ex.getMessage()));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorBody> handleSessionNotFound(SessionNotFoundException ex) {
        // Expirada e inexistente responden igual, para no revelar cuál fue.
        log.debug("Session not found or expired: {}", ex.getSessionId().asString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorBody("session_not_found", ex.getMessage()));
    }

    @ExceptionHandler(AdminAccessDeniedException.class)
    public ResponseEntity<ErrorBody> handleAdminAccessDenied(AdminAccessDeniedException ex) {
        log.warn("Admin access denied, offending CN='{}'", ex.getActualCn());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorBody("admin_access_denied",
                "cert CN not authorized for admin operation"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
            fieldErrors.put(fe.getField(),
                fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()));
        Map<String, Object> body = new HashMap<>();
        body.put("code", "validation_error");
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorBody("invalid_argument", ex.getMessage()));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorBody> handleAuthFallback(AuthException ex) {
        log.error("Unhandled auth exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorBody("internal_error", ex.getMessage()));
    }
}
