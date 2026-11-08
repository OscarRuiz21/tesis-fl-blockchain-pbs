package org.fl.coordinator.service;

import lombok.RequiredArgsConstructor;
import org.fl.coordinator.model.ClientMetrics;
import org.fl.coordinator.model.ExperimentReport;
import org.fl.coordinator.model.ExperimentRun;
import org.fl.coordinator.store.FileMetricsReader;
import org.fl.coordinator.store.ReportGenerator;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/** Escribe el {@code run_summary.json} de una corrida a partir de los CSV de los clientes. */
@Service
@RequiredArgsConstructor
public class RunSummaryService {

    private final FileMetricsReader reader;
    private final ReportGenerator reportGen;

    public ExperimentReport generateRunSummary(ExperimentRun run, Path outputDir) {
        List<ClientMetrics> clientMetrics = reader.readAll(run.spec().label(), run.spec().k());
        return reportGen.writeRunSummary(run, clientMetrics, outputDir);
    }
}
