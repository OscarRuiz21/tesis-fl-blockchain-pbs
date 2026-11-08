package org.fl.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fl.client.model.ClientState;
import org.fl.client.service.AnonymityService;
import org.fl.client.service.ClientStateHolder;
import org.fl.client.service.RunAllRoundsService;
import org.fl.client.store.ClientBootstrap;
import org.fl.client.store.ExperimentBootstrap;
import org.fl.client.store.FileMetricsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientLifecycleController.class)
@Import(GlobalExceptionHandler.class)
class ClientLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RunAllRoundsService runAllRounds;

    @MockBean
    private ClientStateHolder state;

    @MockBean
    private ClientBootstrap bootstrap;

    @MockBean
    private ExperimentBootstrap experimentBootstrap;

    @MockBean
    private AnonymityService anonymityService;

    @MockBean
    private FileMetricsStore metricsStore;

    @MockBean
    private ExecutorService experimentExecutor;

    private final ObjectMapper jackson = new ObjectMapper();

    // Ejecutor en línea: el experimento corre en el hilo del test.
    private ExecutorService inline;

    @BeforeEach
    void setUp() {
        inline = Executors.newSingleThreadExecutor();
        org.mockito.Mockito.when(experimentExecutor.submit(org.mockito.ArgumentMatchers.<Runnable>any()))
            .thenAnswer(inv -> inline.submit((Runnable) inv.getArgument(0)));
    }

    @Test
    void startReturns202WhenIdle() throws Exception {
        org.mockito.Mockito.when(state.compareAndSet(ClientState.IDLE, ClientState.RUNNING))
            .thenReturn(true);
        org.mockito.Mockito.when(bootstrap.clientId())
            .thenReturn(org.fl.client.model.ClientId.of("client-1"));

        var body = Map.of(
            "clientId", "client-1",
            "experimentLabel", "smoke",
            "totalRounds", 1,
            "modelName", "logistic_regression",
            "numParams", 2,
            "datasetPath", "/data",
            "aggregationStrategy", "fedavg",
            "anonymityMode", "pbs",
            "hyperparams", Map.of("epochs", 1));

        mockMvc.perform(post("/lifecycle/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jackson.writeValueAsString(body)))
            .andExpect(status().isAccepted());

        inline.shutdown();
        boolean done = inline.awaitTermination(2, TimeUnit.SECONDS);
        assertThat(done).isTrue();
    }

    @Test
    void startReturns409WhenAlreadyRunning() throws Exception {
        org.mockito.Mockito.when(state.compareAndSet(ClientState.IDLE, ClientState.RUNNING))
            .thenReturn(false);

        var body = Map.of(
            "clientId", "client-1",
            "experimentLabel", "smoke",
            "totalRounds", 1,
            "modelName", "logistic_regression",
            "numParams", 2,
            "datasetPath", "/data",
            "aggregationStrategy", "fedavg",
            "anonymityMode", "pbs",
            "hyperparams", Map.of());

        mockMvc.perform(post("/lifecycle/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jackson.writeValueAsString(body)))
            .andExpect(status().isConflict());

        // Quien pierde el CAS no cambia el modo ni borra CSV.
        verify(anonymityService, never()).applyMode(org.mockito.ArgumentMatchers.anyString());
        verify(metricsStore, never()).resetForLabel(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void startAppliesModeAndResetsCsvsAfterCasWins() throws Exception {
        org.mockito.Mockito.when(state.compareAndSet(ClientState.IDLE, ClientState.RUNNING))
            .thenReturn(true);
        org.mockito.Mockito.when(bootstrap.clientId())
            .thenReturn(org.fl.client.model.ClientId.of("client-1"));

        var body = Map.of(
            "clientId", "client-1",
            "experimentLabel", "gmsc-K10-R8-fedavg-none",
            "totalRounds", 8,
            "modelName", "mlp",
            "numParams", 321,
            "datasetPath", "/data",
            "aggregationStrategy", "fedavg",
            "anonymityMode", "none",
            "hyperparams", Map.of("epochs", 1));

        mockMvc.perform(post("/lifecycle/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jackson.writeValueAsString(body)))
            .andExpect(status().isAccepted());

        // El CAS precede a toda mutación, y applyMode a resetForLabel.
        InOrder order = inOrder(state, anonymityService, metricsStore);
        order.verify(state).compareAndSet(ClientState.IDLE, ClientState.RUNNING);
        order.verify(anonymityService).applyMode("none");
        order.verify(metricsStore).resetForLabel("gmsc-K10-R8-fedavg-none");

        inline.shutdown();
        inline.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void startWithInvalidAnonymityModeReturns400() throws Exception {
        var body = Map.of(
            "clientId", "client-1",
            "experimentLabel", "smoke",
            "totalRounds", 1,
            "modelName", "logistic_regression",
            "numParams", 2,
            "datasetPath", "/data",
            "aggregationStrategy", "fedavg",
            "anonymityMode", "garbage",
            "hyperparams", Map.of());

        mockMvc.perform(post("/lifecycle/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jackson.writeValueAsString(body)))
            .andExpect(status().isBadRequest());

        // La validación rechaza la petición antes de tocar el estado.
        verify(state, never()).compareAndSet(ClientState.IDLE, ClientState.RUNNING);
        verify(anonymityService, never()).applyMode(org.mockito.ArgumentMatchers.anyString());
        verify(metricsStore, never()).resetForLabel(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void statusReturnsCurrentState() throws Exception {
        org.mockito.Mockito.when(bootstrap.clientIdOrNull()).thenReturn("client-1");
        org.mockito.Mockito.when(state.get()).thenReturn(ClientState.RUNNING);

        mockMvc.perform(get("/lifecycle/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientId").value("client-1"))
            .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    void statusBeforeAssignReturnsNullClientId() throws Exception {
        org.mockito.Mockito.when(bootstrap.clientIdOrNull()).thenReturn(null);
        org.mockito.Mockito.when(state.get()).thenReturn(ClientState.IDLE);

        mockMvc.perform(get("/lifecycle/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientId").doesNotExist())
            .andExpect(jsonPath("$.state").value("IDLE"));
    }

    @Test
    void startWithMissingFieldsReturns400() throws Exception {
        var body = Map.of(
            "clientId", "",  // blank
            "experimentLabel", "smoke",
            "totalRounds", 1,
            "modelName", "logistic_regression",
            "numParams", 2,
            "datasetPath", "/data",
            "aggregationStrategy", "fedavg",
            "anonymityMode", "pbs",
            "hyperparams", Map.of());

        mockMvc.perform(post("/lifecycle/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jackson.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }
}
