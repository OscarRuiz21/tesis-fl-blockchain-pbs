package org.fl.coordinator.controller;

import org.fl.coordinator.exception.ExperimentAbortedException;
import org.fl.coordinator.exception.InvalidExperimentSpecException;
import org.fl.coordinator.exception.InvalidExperimentSpecException.ValidationError;
import org.fl.coordinator.model.ExperimentReport;
import org.fl.coordinator.model.ExperimentRun;
import org.fl.coordinator.model.ExperimentSpec;
import org.fl.coordinator.service.RunExperimentService;
import org.fl.coordinator.service.RunSummaryService;
import org.fl.coordinator.service.SpecValidator;
import org.fl.coordinator.service.YamlSpecParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el mapeo HTTP del controller: códigos de estado y forma del JSON,
 * incluido el {@code GlobalExceptionHandler}. Los services colaborantes se
 * mockean.
 */
@WebMvcTest(controllers = ExperimentController.class)
@Import(GlobalExceptionHandler.class)
class ExperimentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private YamlSpecParser parser;

    @MockBean
    private SpecValidator validator;

    @MockBean
    private RunExperimentService runExperiment;

    @MockBean
    private RunSummaryService runSummary;

    private static final String SAMPLE_YAML = "any-yaml-body";

    @Test
    void returns200WithReportOnHappyPath() throws Exception {
        ExperimentSpec spec = sampleSpec();
        when(parser.parse(any())).thenReturn(spec);
        // validator no-op (no excepción).
        Instant t0 = Instant.parse("2026-05-17T10:00:00Z");
        ExperimentRun run = new ExperimentRun(spec, t0, t0.plusSeconds(120), List.of(), 2, 0);
        when(runExperiment.run(any())).thenReturn(run);
        ExperimentReport report = new ExperimentReport(
            "smoke-K2",
            Path.of("/results/smoke-K2/run_summary.json"),
            Path.of("/results/smoke-K2/global_eval.csv"),
            Path.of("/results/smoke-K2"),
            2, 1);
        when(runSummary.generateRunSummary(any(), any())).thenReturn(report);

        mvc.perform(post("/experiments/run")
                .contentType("application/x-yaml")
                .content(SAMPLE_YAML))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("smoke-K2"))
            .andExpect(jsonPath("$.k").value(2))
            .andExpect(jsonPath("$.totalRounds").value(1))
            .andExpect(jsonPath("$.totalSeconds").value(120.0))
            .andExpect(jsonPath("$.runSummaryPath").value("/results/smoke-K2/run_summary.json"));
    }

    @Test
    void returns400OnInvalidSpec() throws Exception {
        ExperimentSpec spec = sampleSpec();
        when(parser.parse(any())).thenReturn(spec);
        doThrow(new InvalidExperimentSpecException(List.of(
                new ValidationError("modelName", "not in MODEL_REGISTRY whitelist: foo"))))
            .when(validator).validate(any());

        mvc.perform(post("/experiments/run")
                .contentType(MediaType.TEXT_PLAIN)
                .content(SAMPLE_YAML))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("invalid_spec"))
            .andExpect(jsonPath("$.errors[0].field").value("modelName"));
    }

    @Test
    void returns500WhenExperimentAborts() throws Exception {
        ExperimentSpec spec = sampleSpec();
        when(parser.parse(any())).thenReturn(spec);
        when(runExperiment.run(any())).thenThrow(
            new ExperimentAbortedException("client-3 never reached IDLE"));

        mvc.perform(post("/experiments/run")
                .contentType("application/x-yaml")
                .content(SAMPLE_YAML))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("experiment_aborted"));
    }

    @Test
    void returns400OnYamlParseError() throws Exception {
        when(parser.parse(any())).thenThrow(
            new IllegalArgumentException("YAML parse error: bad indent at line 3"));

        mvc.perform(post("/experiments/run")
                .contentType("application/x-yaml")
                .content("not a real yaml"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("invalid_argument"));
    }

    @Test
    void validateReturns200WithValidTrueOnGoodSpec() throws Exception {
        when(parser.parse(any())).thenReturn(sampleSpec());
        // validator no-op (no excepción).

        mvc.perform(post("/experiments/validate")
                .contentType("application/x-yaml")
                .content(SAMPLE_YAML))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test
    void validateReturns200WithValidFalseAndErrorsOnBadSpec() throws Exception {
        when(parser.parse(any())).thenReturn(sampleSpec());
        doThrow(new InvalidExperimentSpecException(List.of(
                new ValidationError("modelName", "not in MODEL_REGISTRY whitelist: foo"),
                new ValidationError("k", "must be >= 1, got 0"))))
            .when(validator).validate(any());

        mvc.perform(post("/experiments/validate")
                .contentType("application/x-yaml")
                .content(SAMPLE_YAML))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors.length()").value(2))
            .andExpect(jsonPath("$.errors[0].field").value("modelName"))
            .andExpect(jsonPath("$.errors[1].field").value("k"));
    }

    @Test
    void validateReturns200WithYamlParseErrorAsValidationError() throws Exception {
        when(parser.parse(any())).thenThrow(
            new IllegalArgumentException("YAML parse error: bad indent at line 3"));

        mvc.perform(post("/experiments/validate")
                .contentType("application/x-yaml")
                .content("not a real yaml"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors[0].field").value("yaml"))
            .andExpect(jsonPath("$.errors[0].message").value(
                org.hamcrest.Matchers.containsString("YAML parse error")));
    }

    private static ExperimentSpec sampleSpec() {
        return new ExperimentSpec(
            "smoke-K2", 2, 1, 42,
            "fedavg", "pbs",
            "logistic_regression", 26,
            "tabular_csv",
            Path.of("/tmp/partitions"),
            Path.of("/tmp/test.csv"),
            "iid", null,
            Map.of("epochs", 5),
            Duration.ofSeconds(60),
            Duration.ofSeconds(180),
            Duration.ofSeconds(3600),
            Path.of("/tmp/results"));
    }
}
