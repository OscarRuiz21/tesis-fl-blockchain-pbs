package org.fl.coordinator.model;

import java.nio.file.Path;
import java.util.Objects;

/** Rutas de los archivos que deja una corrida. */
public record ExperimentReport(
    String experimentLabel,
    Path runSummaryJsonPath,
    Path globalEvalCsvPath,
    Path clientCsvsDir,
    int totalClients,
    int totalRounds
) {
    public ExperimentReport {
        Objects.requireNonNull(experimentLabel, "experimentLabel must not be null");
        Objects.requireNonNull(runSummaryJsonPath, "runSummaryJsonPath must not be null");
        Objects.requireNonNull(globalEvalCsvPath, "globalEvalCsvPath must not be null");
        Objects.requireNonNull(clientCsvsDir, "clientCsvsDir must not be null");
    }
}
