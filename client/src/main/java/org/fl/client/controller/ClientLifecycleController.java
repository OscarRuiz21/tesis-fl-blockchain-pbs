package org.fl.client.controller;

import jakarta.validation.Valid;
import org.fl.client.exception.TrainingFailedException;
import org.fl.client.model.ClientId;
import org.fl.client.model.ClientState;
import org.fl.client.service.AnonymityService;
import org.fl.client.service.ClientStateHolder;
import org.fl.client.service.RunAllRoundsService;
import org.fl.client.store.ClientBootstrap;
import org.fl.client.store.ExperimentBootstrap;
import org.fl.client.store.FileMetricsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/**
 * Control del ciclo de vida que usa el coordinator. {@code start} responde
 * 202 y corre el experimento en segundo plano; a partir de ahí el cliente
 * sigue el protocolo por su cuenta y el coordinator solo sondea
 * {@code status}.
 */
@RestController
@RequestMapping("/lifecycle")
public class ClientLifecycleController {

    private static final Logger log = LoggerFactory.getLogger(ClientLifecycleController.class);

    private final RunAllRoundsService runAllRounds;
    private final ClientStateHolder state;
    private final ExecutorService experimentExecutor;
    private final ClientBootstrap bootstrap;
    private final ExperimentBootstrap experimentBootstrap;
    private final AnonymityService anonymityService;
    private final FileMetricsStore metricsStore;

    public ClientLifecycleController(RunAllRoundsService runAllRounds,
                                     ClientStateHolder state,
                                     ExecutorService experimentExecutor,
                                     ClientBootstrap bootstrap,
                                     ExperimentBootstrap experimentBootstrap,
                                     AnonymityService anonymityService,
                                     FileMetricsStore metricsStore) {
        this.runAllRounds = runAllRounds;
        this.state = state;
        this.experimentExecutor = experimentExecutor;
        this.bootstrap = bootstrap;
        this.experimentBootstrap = experimentBootstrap;
        this.anonymityService = anonymityService;
        this.metricsStore = metricsStore;
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(@Valid @RequestBody LifecycleDtos.StartExperimentRequest req) {
        // La réplica no conoce su clientId ni su etiqueta hasta este punto; la
        // etiqueta define el subdirectorio de métricas.
        bootstrap.assign(ClientId.of(req.clientId()));
        experimentBootstrap.assign(req.experimentLabel());

        if (!state.compareAndSet(ClientState.IDLE, ClientState.RUNNING)) {
            return ResponseEntity.status(409).build();
        }

        // Solo quien gana el CAS fija el modo y borra los CSV previos de la
        // etiqueta; así un reintento o un doble clic en el portal no pisa la
        // corrida en curso.
        anonymityService.applyMode(req.anonymityMode());
        metricsStore.resetForLabel(req.experimentLabel());

        ClientId me = bootstrap.clientId();
        log.info("[client={}] Starting experiment '{}' for {} rounds (model={}, numParams={})",
            me.value(), req.experimentLabel(), req.totalRounds(),
            req.modelName(), req.numParams());

        Path datasetPath = Path.of(req.datasetPath());

        experimentExecutor.submit(() -> {
            try {
                runAllRounds.runAll(
                    me, req.experimentLabel(), req.totalRounds(),
                    req.modelName(), req.numParams(),
                    datasetPath, req.hyperparams());
                state.set(ClientState.DONE);
                log.info("[client={}] Experiment finished successfully", me.value());
            } catch (Exception e) {
                log.error("[client={}] Experiment failed", me.value(), e);
                if (e instanceof TrainingFailedException tfe) {
                    log.error("[client={}] Subprocess stderr (exit={}):\n{}",
                        me.value(), tfe.getExitCode(), tfe.getStderr());
                }
                state.set(ClientState.ERROR);
            }
        });

        return ResponseEntity.accepted().build();
    }

    @GetMapping("/status")
    public LifecycleDtos.StatusResponse status() {
        // El coordinator sondea antes de asignar el clientId; se devuelve null.
        return new LifecycleDtos.StatusResponse(bootstrap.clientIdOrNull(), state.get().name());
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stop(@RequestBody(required = false) LifecycleDtos.StopRequest req) {
        // Solo cambia el estado; el hilo del experimento sigue hasta terminar.
        if (state.get() == ClientState.RUNNING) {
            log.warn("[client={}] /stop received while RUNNING; state set to IDLE but " +
                "experiment thread continues until naturally finishes",
                bootstrap.clientIdOrNull());
        }
        state.set(ClientState.IDLE);
        return ResponseEntity.ok().build();
    }
}
