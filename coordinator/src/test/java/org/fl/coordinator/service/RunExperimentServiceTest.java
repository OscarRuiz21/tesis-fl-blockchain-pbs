package org.fl.coordinator.service;

import org.fl.coordinator.exception.ExperimentAbortedException;
import org.fl.coordinator.gateway.BlockchainGateway;
import org.fl.coordinator.gateway.ClientControlGateway;
import org.fl.coordinator.gateway.PythonEvalGateway;
import org.fl.coordinator.model.ClientStatus;
import org.fl.coordinator.model.EvaluationResult;
import org.fl.coordinator.model.ExperimentRun;
import org.fl.coordinator.model.ExperimentSpec;
import org.fl.coordinator.model.GlobalModel;
import org.fl.coordinator.model.RoundId;
import org.fl.coordinator.model.RoundStatus;
import org.fl.coordinator.store.GlobalEvalCsvWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunExperimentServiceTest {

    /**
     * Smoke: una ronda completa con K=2 clientes. El polling avanza tras el
     * primer query y la eval se invoca una vez.
     */
    @Test
    void completesSingleRoundAndEmitsOneEval() {
        BootstrapProtocolService bootstrap = mock(BootstrapProtocolService.class);
        ClientControlGateway clientControl = mock(ClientControlGateway.class);
        BlockchainGateway blockchain = mock(BlockchainGateway.class);
        PythonEvalGateway evalGateway = mock(PythonEvalGateway.class);
        GlobalEvalCsvWriter evalWriter = mock(GlobalEvalCsvWriter.class);

        // Bootstrap no-op.
        doNothing().when(bootstrap).bootstrap(any());

        // Clientes responden IDLE inmediatamente, después DONE.
        when(clientControl.queryStatus(any())).thenReturn(ClientStatus.IDLE, ClientStatus.IDLE,
            ClientStatus.DONE, ClientStatus.DONE);

        // Polling: round 1 activa, después avanza a round 2.
        when(blockchain.queryRoundStatus()).thenReturn(
            new RoundStatus(RoundId.of(2), 2, "fedavg"));

        // Modelo agregado de la ronda 1 (vive bajo roundId=2 según convención).
        byte[] modelBytes = new byte[10 * 4];  // 10 floats
        when(blockchain.queryGlobalModel(any())).thenReturn(
            GlobalModel.of(modelBytes, RoundId.of(2), null));

        when(evalGateway.evaluate(any(), any())).thenReturn(
            new EvaluationResult(0.78, 0.31, Duration.ofMillis(500)));

        Clock clock = Clock.fixed(Instant.parse("2026-05-17T10:00:00Z"), ZoneOffset.UTC);
        RunExperimentService service = new RunExperimentService(
            bootstrap, clientControl, blockchain, evalGateway, evalWriter,
            clock, 1, 60000, 60000);

        ExperimentSpec spec = sampleSpec("test", 2, 1);
        ExperimentRun run = service.run(spec);

        assertThat(run.globalEvals()).hasSize(1);
        assertThat(run.globalEvals().get(0).accuracy()).isEqualTo(0.78);
        verify(evalGateway, atLeastOnce()).evaluate(any(), any());
        verify(evalWriter, atLeastOnce()).append(eq("test"), any());
    }

    /**
     * Si el polling no avanza nunca, el timeout global dispara
     * ExperimentAbortedException.
     */
    @Test
    void abortsOnGlobalTimeout() {
        BootstrapProtocolService bootstrap = mock(BootstrapProtocolService.class);
        ClientControlGateway clientControl = mock(ClientControlGateway.class);
        BlockchainGateway blockchain = mock(BlockchainGateway.class);
        PythonEvalGateway evalGateway = mock(PythonEvalGateway.class);
        GlobalEvalCsvWriter evalWriter = mock(GlobalEvalCsvWriter.class);

        doNothing().when(bootstrap).bootstrap(any());
        when(clientControl.queryStatus(any())).thenReturn(ClientStatus.IDLE);
        // Ronda NUNCA avanza: el queryRoundStatus siempre devuelve la misma.
        when(blockchain.queryRoundStatus()).thenReturn(
            new RoundStatus(RoundId.of(1), 1, "fedavg"));

        // Reloj real para que el global timeout dispare.
        Clock clock = Clock.systemUTC();
        RunExperimentService service = new RunExperimentService(
            bootstrap, clientControl, blockchain, evalGateway, evalWriter,
            clock,
            10,    // polling 10ms
            100,   // global timeout 100ms, dispara rápido
            60000);

        ExperimentSpec spec = sampleSpec("timeout-exp", 1, 1);
        assertThatThrownBy(() -> service.run(spec))
            .isInstanceOf(ExperimentAbortedException.class)
            .hasMessageContaining("Global timeout");
    }

    /**
     * Si un cliente nunca llega a IDLE, se lanza ExperimentAbortedException
     * con info del estado esperado.
     */
    @Test
    void abortsWhenClientNeverReachesIdle() {
        BootstrapProtocolService bootstrap = mock(BootstrapProtocolService.class);
        ClientControlGateway clientControl = mock(ClientControlGateway.class);
        BlockchainGateway blockchain = mock(BlockchainGateway.class);
        PythonEvalGateway evalGateway = mock(PythonEvalGateway.class);
        GlobalEvalCsvWriter evalWriter = mock(GlobalEvalCsvWriter.class);

        doNothing().when(bootstrap).bootstrap(any());
        // RUNNING != IDLE: el waitForState nunca matchea y el timeout dispara.
        when(clientControl.queryStatus(any())).thenReturn(ClientStatus.RUNNING);

        Clock clock = Clock.systemUTC();
        RunExperimentService service = new RunExperimentService(
            bootstrap, clientControl, blockchain, evalGateway, evalWriter,
            clock, 10, 60000,
            100);   // client-ready timeout 100ms.

        ExperimentSpec spec = sampleSpec("idle-fail", 1, 1);
        assertThatThrownBy(() -> service.run(spec))
            .isInstanceOf(ExperimentAbortedException.class)
            .hasMessageContaining("IDLE");
    }

    /**
     * El hyperparams del spec llega INTACTO al cliente vía startExperiment.
     */
    @Test
    void hyperparamsArePassedIntactToClient() {
        BootstrapProtocolService bootstrap = mock(BootstrapProtocolService.class);
        ClientControlGateway clientControl = mock(ClientControlGateway.class);
        BlockchainGateway blockchain = mock(BlockchainGateway.class);
        PythonEvalGateway evalGateway = mock(PythonEvalGateway.class);
        GlobalEvalCsvWriter evalWriter = mock(GlobalEvalCsvWriter.class);

        doNothing().when(bootstrap).bootstrap(any());
        when(clientControl.queryStatus(any())).thenReturn(ClientStatus.IDLE, ClientStatus.DONE);
        when(blockchain.queryRoundStatus()).thenReturn(
            new RoundStatus(RoundId.of(2), 1, "fedavg"));
        when(blockchain.queryGlobalModel(any())).thenReturn(
            GlobalModel.of(new byte[40], RoundId.of(2), null));
        when(evalGateway.evaluate(any(), any())).thenReturn(
            new EvaluationResult(0.5, 0.5, Duration.ofMillis(100)));

        Clock clock = Clock.fixed(Instant.parse("2026-05-17T10:00:00Z"), ZoneOffset.UTC);
        RunExperimentService service = new RunExperimentService(
            bootstrap, clientControl, blockchain, evalGateway, evalWriter,
            clock, 1, 60000, 60000);

        ExperimentSpec spec = sampleSpec("hp-test", 1, 1);
        service.run(spec);

        // Capturamos el spec entero que recibe startExperiment.
        ArgumentCaptor<ExperimentSpec> specCap = ArgumentCaptor.forClass(ExperimentSpec.class);
        verify(clientControl, atLeastOnce()).startExperiment(any(), specCap.capture());

        ExperimentSpec captured = specCap.getValue();
        assertThat(captured.hyperparams())
            .containsEntry("epochs", 5)
            .containsEntry("lr", 0.01);
    }

    private static ExperimentSpec sampleSpec(String label, int k, int totalRounds) {
        return new ExperimentSpec(
            label, k, totalRounds, 42,
            "fedavg", "pbs",
            "logistic_regression", 10,
            "tabular_csv",
            Path.of("/tmp/partitions"),
            Path.of("/tmp/test.csv"),
            "iid", null,
            Map.of("epochs", 5, "lr", 0.01),
            Duration.ofSeconds(60),
            Duration.ofSeconds(180),
            Duration.ofSeconds(3600),
            Path.of("/tmp/results"));
    }
}
