package org.fl.coordinator.controller;

import org.fl.coordinator.controller.ExperimentDtos.ErrorBody;
import org.fl.coordinator.exception.AuthenticationFailedException;
import org.fl.coordinator.exception.BlockchainUnavailableException;
import org.fl.coordinator.exception.ClientControlException;
import org.fl.coordinator.exception.CoordinatorException;
import org.fl.coordinator.exception.ExperimentAbortedException;
import org.fl.coordinator.exception.InvalidExperimentSpecException;
import org.fl.coordinator.exception.ModelEvaluationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Traduce las excepciones a códigos HTTP. Las fallas del AS se responden 502
 * porque el coordinator actúa como cliente de un servicio externo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidExperimentSpecException.class)
    public ResponseEntity<ErrorBody> handleInvalidSpec(InvalidExperimentSpecException ex) {
        log.warn("Spec validation failed: {} error(s)", ex.getErrors().size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorBody("invalid_spec", ex.getMessage(), ex.getErrors()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorBody("invalid_argument", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorBody> handleAuth(AuthenticationFailedException ex) {
        log.error("AS gateway failure", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorBody("as_unavailable", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(BlockchainUnavailableException.class)
    public ResponseEntity<ErrorBody> handleBlockchain(BlockchainUnavailableException ex) {
        log.error("Blockchain gateway failure", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorBody("blockchain_unavailable", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(ExperimentAbortedException.class)
    public ResponseEntity<ErrorBody> handleAborted(ExperimentAbortedException ex) {
        log.error("Experiment aborted", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorBody("experiment_aborted", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(ModelEvaluationFailedException.class)
    public ResponseEntity<ErrorBody> handleEvalFailed(ModelEvaluationFailedException ex) {
        log.error("Model eval failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorBody("eval_failed", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(ClientControlException.class)
    public ResponseEntity<ErrorBody> handleClientControl(ClientControlException ex) {
        log.error("Client control failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorBody("client_control_failed", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(CoordinatorException.class)
    public ResponseEntity<ErrorBody> handleFallback(CoordinatorException ex) {
        log.error("Unhandled coordinator exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorBody("internal_error", ex.getMessage(), List.of()));
    }
}
