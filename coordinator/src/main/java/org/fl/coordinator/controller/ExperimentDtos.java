package org.fl.coordinator.controller;

import org.fl.coordinator.exception.InvalidExperimentSpecException.ValidationError;

import java.util.List;

/** Cuerpos HTTP de {@code /experiments}. */
public final class ExperimentDtos {

    private ExperimentDtos() {
        throw new AssertionError("utility class");
    }

    /** {@code finalAccuracy} y {@code finalLoss} son {@code null} si no se evaluó ninguna ronda. */
    public record ExperimentReportResponse(
        String label,
        int k,
        int totalRounds,
        Double finalAccuracy,
        Double finalLoss,
        double totalSeconds,
        String runSummaryPath,
        String globalEvalCsvPath,
        String clientsCsvDir
    ) {}

    /** {@code errors} solo se llena cuando falla la validación de la spec. */
    public record ErrorBody(
        String code,
        String message,
        List<ValidationError> errors
    ) {}

    public record ValidateResponse(
        boolean valid,
        List<ValidationError> errors
    ) {}
}
