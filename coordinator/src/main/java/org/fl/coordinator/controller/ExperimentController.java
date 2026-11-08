package org.fl.coordinator.controller;

import lombok.RequiredArgsConstructor;
import org.fl.coordinator.controller.ExperimentDtos.ExperimentReportResponse;
import org.fl.coordinator.controller.ExperimentDtos.ValidateResponse;
import org.fl.coordinator.exception.InvalidExperimentSpecException;
import org.fl.coordinator.model.ExperimentReport;
import org.fl.coordinator.model.ExperimentRun;
import org.fl.coordinator.model.ExperimentSpec;
import org.fl.coordinator.model.GlobalEvalRecord;
import org.fl.coordinator.service.RunExperimentService;
import org.fl.coordinator.service.RunSummaryService;
import org.fl.coordinator.service.SpecValidator;
import org.fl.coordinator.service.YamlSpecParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * {@code POST /experiments/run} recibe el YAML del experimento y bloquea hasta
 * que termina o se aborta. Acepta también {@code application/octet-stream}
 * porque run-experiment.sh lo envía con {@code curl --data-binary}.
 */
@RestController
@RequestMapping("/experiments")
@RequiredArgsConstructor
public class ExperimentController {

    private static final Logger log = LoggerFactory.getLogger(ExperimentController.class);

    private final YamlSpecParser parser;
    private final SpecValidator validator;
    private final RunExperimentService runExperiment;
    private final RunSummaryService runSummary;

    @PostMapping(
        value = "/run",
        consumes = {"application/x-yaml", "application/yaml", "application/octet-stream", MediaType.TEXT_PLAIN_VALUE},
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ExperimentReportResponse> run(@RequestBody String yamlBody) {
        log.info("Received experiment request ({} bytes)", yamlBody.length());

        ExperimentSpec spec = parser.parse(yamlBody);
        validator.validate(spec);
        log.info("Spec validated: label={} k={} rounds={} model={} agg={} mode={}",
            spec.label(), spec.k(), spec.totalRounds(),
            spec.modelName(), spec.aggregationStrategy(), spec.anonymityMode());

        ExperimentRun run = runExperiment.run(spec);
        ExperimentReport report = runSummary.generateRunSummary(run, spec.resultsDir());

        return ResponseEntity.ok(toResponse(run, report));
    }

    /**
     * Valida sin ejecutar. Responde 200 aun con errores, que el portal muestra
     * en el formulario; {@code /run} responde 400.
     */
    @PostMapping(
        value = "/validate",
        consumes = {"application/x-yaml", "application/yaml", "application/octet-stream", MediaType.TEXT_PLAIN_VALUE},
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ValidateResponse> validate(@RequestBody String yamlBody) {
        log.info("Received validation request ({} bytes)", yamlBody.length());
        ExperimentSpec spec;
        try {
            spec = parser.parse(yamlBody);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new ValidateResponse(false, List.of(
                new InvalidExperimentSpecException.ValidationError("yaml", e.getMessage()))));
        }
        try {
            validator.validate(spec);
            return ResponseEntity.ok(new ValidateResponse(true, List.of()));
        } catch (InvalidExperimentSpecException e) {
            return ResponseEntity.ok(new ValidateResponse(false, e.getErrors()));
        }
    }

    private static ExperimentReportResponse toResponse(ExperimentRun run, ExperimentReport report) {
        List<GlobalEvalRecord> evals = run.globalEvals();
        Double finalAccuracy = evals.isEmpty() ? null : evals.get(evals.size() - 1).accuracy();
        Double finalLoss = evals.isEmpty() ? null : evals.get(evals.size() - 1).loss();
        double totalSeconds =
            Duration.between(run.startedAt(), run.finishedAt()).toMillis() / 1000.0;
        return new ExperimentReportResponse(
            report.experimentLabel(),
            report.totalClients(),
            report.totalRounds(),
            finalAccuracy,
            finalLoss,
            totalSeconds,
            report.runSummaryJsonPath().toString(),
            report.globalEvalCsvPath().toString(),
            report.clientCsvsDir().toString());
    }
}
