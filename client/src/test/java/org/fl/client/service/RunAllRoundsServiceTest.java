package org.fl.client.service;

import org.fl.client.exception.ClientRoundFailedException;
import org.fl.client.gateway.BlockchainGateway;
import org.fl.client.model.AggregationResult;
import org.fl.client.model.ClientId;
import org.fl.client.model.ClientState;
import org.fl.client.model.ExperimentRun;
import org.fl.client.model.PhaseTimings;
import org.fl.client.model.RoundId;
import org.fl.client.model.RoundResult;
import org.fl.client.model.RoundStatus;
import org.fl.client.model.ModelUpdate;
import org.fl.client.model.TrainingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunAllRoundsServiceTest {

    private RunRoundService runRound;
    private BlockchainGateway blockchain;
    private AuditService audit;
    private Clock clock;
    private ClientId me;

    @BeforeEach
    void setUp() {
        runRound = mock(RunRoundService.class);
        blockchain = mock(BlockchainGateway.class);
        audit = mock(AuditService.class);
        clock = Clock.systemUTC();
        me = ClientId.of("client-1");
    }

    @Test
    void completesAllRoundsWhenStatusAdvances() {
        byte[] updateBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.01f).putFloat(0.02f).array();
        TrainingResult t = new TrainingResult(
            ModelUpdate.of(updateBytes), 0.5, 0.8, 10, Duration.ofMillis(5));
        PhaseTimings zero = PhaseTimings.zero();
        RoundResult fake = new RoundResult(
            RoundId.of(1), me, t, zero,
            Duration.ZERO, true, Duration.ZERO,
            (AggregationResult.ChaincodeTimings) null, Duration.ZERO);

        when(runRound.runRound(eq(me), any(), anyString(), anyInt(), any(), any()))
            .thenReturn(fake);

        AtomicInteger pollCounter = new AtomicInteger();
        when(blockchain.queryRoundStatus()).thenAnswer(inv -> {
            int n = pollCounter.incrementAndGet();
            // A partir del segundo sondeo el ledger ya avanzó a la ronda 2.
            long active = (n <= 1) ? 1 : 2;
            return new RoundStatus(RoundId.of(active), 1, "fedavg");
        });

        RunAllRoundsService svc = new RunAllRoundsService(
            runRound, blockchain, audit, clock, 1L, 5000L);

        ExperimentRun run = svc.runAll(
            me, "test-label", 2, "logistic_regression", 2,
            Path.of("/data"), Map.of());

        assertThat(run.finalState()).isEqualTo(ClientState.DONE);
        assertThat(run.rounds()).hasSize(2);
        verify(runRound).runRound(eq(me), eq(RoundId.of(1)), anyString(), anyInt(), any(), any());
        verify(runRound).runRound(eq(me), eq(RoundId.of(2)), anyString(), anyInt(), any(), any());
    }

    @Test
    void waitForRoundTimeoutTriggersClientRoundFailed() {
        // runRound de la ronda 1 retorna normal (con Δw para la auditoría);
        // el timeout ocurre esperando el avance a la ronda 2.
        byte[] updateBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.01f).putFloat(0.02f).array();
        TrainingResult t = new TrainingResult(
            ModelUpdate.of(updateBytes), 0.5, 0.8, 10, Duration.ofMillis(5));
        RoundResult fake = new RoundResult(
            RoundId.of(1), me, t, PhaseTimings.zero(),
            Duration.ZERO, true, Duration.ZERO,
            (AggregationResult.ChaincodeTimings) null, Duration.ZERO);
        when(runRound.runRound(eq(me), any(), anyString(), anyInt(), any(), any()))
            .thenReturn(fake);

        // La ronda activa nunca avanza.
        when(blockchain.queryRoundStatus())
            .thenReturn(new RoundStatus(RoundId.of(1), 1, "fedavg"));

        RunAllRoundsService svc = new RunAllRoundsService(
            runRound, blockchain, audit, clock, 1L, 50L);

        assertThatThrownBy(() -> svc.runAll(
            me, "test-label", 5, "logistic_regression", 2,
            Path.of("/data"), Map.of()))
            .isInstanceOf(ClientRoundFailedException.class);
    }
}
