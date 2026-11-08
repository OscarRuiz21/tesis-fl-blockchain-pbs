package org.fl.client.service;

import org.fl.client.exception.AlreadyAggregatedException;
import org.fl.client.exception.BlockchainUnavailableException;
import org.fl.client.gateway.BlockchainGateway;
import org.fl.client.gateway.FabricErrorTranslator;
import org.fl.client.gateway.PythonTrainingGateway;
import org.fl.client.model.AggregationResult;
import org.fl.client.model.AnonId;
import org.fl.client.model.ClientId;
import org.fl.client.model.Credential;
import org.fl.client.model.GlobalModel;
import org.fl.client.model.LeaderInfo;
import org.fl.client.model.ModelUpdate;
import org.fl.client.model.PhaseTimings;
import org.fl.client.model.PreparedSubmission;
import org.fl.client.model.RoundId;
import org.fl.client.model.RoundResult;
import org.fl.client.model.SubmitResponse;
import org.fl.client.model.TallyHash;
import org.fl.client.model.TrainingResult;
import org.fl.client.crypto.PBSDomainParams;
import org.fl.client.store.FileMetricsStore;
import org.fl.client.time.SystemNanoTimeSource;
import org.fl.client.time.TimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La Etapa 3 se decide por elección de líder con {@code getRoundLeaderInfo},
 * no por {@code is_last}. Se sondea cada 1 ms, sin jitter y con paciencia 2.
 */
class RunRoundServiceTest {

    private static final long FAST_POLL_BASE_MS = 1;
    private static final long FAST_JITTER_MS = 0;
    private static final int FAST_MAX_POLLS = 20;
    private static final int FAST_PATIENCE = 2;

    private AnonymityService anonymity;
    private BlockchainGateway blockchain;
    private FabricErrorTranslator errorTranslator;
    private PythonTrainingGateway training;
    private FileMetricsStore metrics;
    private TimeSource time;

    private ClientId me;
    private RoundId roundId;
    private GlobalModel baseModel;
    private TrainingResult trainResult;
    private PreparedSubmission prep;
    private String myAnonCidHex;  // hex del anonCid que el setUp produce

    @BeforeEach
    void setUp() {
        anonymity = mock(AnonymityService.class);
        blockchain = mock(BlockchainGateway.class);
        errorTranslator = mock(FabricErrorTranslator.class);
        training = mock(PythonTrainingGateway.class);
        metrics = mock(FileMetricsStore.class);
        time = new SystemNanoTimeSource();

        me = ClientId.of("client-1");
        roundId = RoundId.of(1);

        byte[] modelBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.1f).putFloat(0.2f).array();
        baseModel = GlobalModel.of(modelBytes, RoundId.of(1), null);

        byte[] updateBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.01f).putFloat(0.02f).array();
        trainResult = new TrainingResult(
            ModelUpdate.of(updateBytes), 0.5, 0.8, 100, Duration.ofMillis(50));

        PBSDomainParams params = PBSDomainParams.p256();
        AnonId anonCid = AnonId.of(params.G().multiply(BigInteger.valueOf(7)).normalize());
        myAnonCidHex = anonCid.toUncompressedHex();
        Credential cred = new Credential(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE);
        PhaseTimings timings = PhaseTimings.zero();
        prep = new PreparedSubmission(anonCid, cred, timings);

        when(anonymity.prepareSubmission(eq(me), eq(roundId))).thenReturn(prep);
        when(anonymity.signUpdate(eq(roundId), any())).thenReturn(new byte[]{1});
        when(blockchain.queryGlobalModel(eq(roundId))).thenReturn(baseModel);
        when(training.train(anyString(), anyInt(), any(), any(), any(), any()))
            .thenReturn(trainResult);

        // submitUpdate siempre responde is_last=false.
        SubmitResponse sentinel = new SubmitResponse(
            false, -1, 1,
            new SubmitResponse.SubmitTimings(
                Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
            1);
        when(blockchain.submitUpdate(any(), any(), any(), any(), eq(roundId))).thenReturn(sentinel);
    }

    private RunRoundService newService() {
        return new RunRoundService(
            anonymity, blockchain, errorTranslator, training, metrics, time,
            FAST_POLL_BASE_MS, FAST_JITTER_MS, FAST_MAX_POLLS, FAST_PATIENCE);
    }

    @Test
    void invokesAggregateWhenIAmPrimaryLeader() {
        // Primer sondeo: ronda lista y este cliente es el menor, así que es líder.
        when(blockchain.queryLeaderInfo(eq(roundId))).thenReturn(
            new LeaderInfo(false, 1, 1, List.of(myAnonCidHex)));

        String tallyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        byte[] modelBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.11f).putFloat(0.21f).array();
        GlobalModel newModel = GlobalModel.of(modelBytes, RoundId.of(2), TallyHash.of(tallyHex));
        AggregationResult agg = new AggregationResult(
            newModel, TallyHash.of(tallyHex), 1,
            new AggregationResult.ChaincodeTimings(
                Duration.ofMillis(1), Duration.ofMillis(2),
                Duration.ofMillis(3), Duration.ofMillis(10)));
        when(blockchain.tryAggregateRound(eq(roundId))).thenReturn(agg);

        RoundResult result = newService().runRound(
            me, roundId, "logistic_regression", 2, Path.of("/data"), Map.of());

        verify(blockchain).tryAggregateRound(eq(roundId));
        assertThat(result.aggregationTriggered()).isTrue();
        assertThat(result.chaincodeTimings()).isNotNull();
    }

    @Test
    void doesNotInvokeAggregateWhenIAmNotLeader() {
        // K=2 y otro anon_cid es menor, así que este cliente no es líder.
        String otherHex = "00".repeat(65);  // menor que cualquier punto P-256 real
        when(blockchain.queryLeaderInfo(eq(roundId)))
            // Primer poll: ronda lista, otro es líder.
            .thenReturn(new LeaderInfo(false, 2, 2, List.of(otherHex, myAnonCidHex)))
            // Próximos polls: modelo ya agregado por el otro líder.
            .thenReturn(new LeaderInfo(true, 2, 2, List.of(otherHex, myAnonCidHex)));

        RoundResult result = newService().runRound(
            me, roundId, "logistic_regression", 2, Path.of("/data"), Map.of());

        verify(blockchain, never()).tryAggregateRound(any());
        assertThat(result.aggregationTriggered()).isFalse();
        assertThat(result.chaincodeTimings()).isNull();
    }

    @Test
    void mvccConflictOnAggregateDoesNotAbortExperiment() {
        // El líder pierde la carrera MVCC y sigue sondeando hasta ver el modelo.
        when(blockchain.queryLeaderInfo(eq(roundId)))
            .thenReturn(new LeaderInfo(false, 1, 1, List.of(myAnonCidHex)))   // soy líder
            .thenReturn(new LeaderInfo(true, 1, 1, List.of(myAnonCidHex)));   // tras MVCC, modelo ya está

        BlockchainUnavailableException mvccEx = new BlockchainUnavailableException(
            new RuntimeException("MVCC_READ_CONFLICT detected"));
        when(blockchain.tryAggregateRound(eq(roundId))).thenThrow(mvccEx);
        when(errorTranslator.isMvccReadConflict(mvccEx)).thenReturn(true);

        RoundResult result = newService().runRound(
            me, roundId, "logistic_regression", 2, Path.of("/data"), Map.of());

        // No abortó; aggregationTriggered queda false (otro cliente lo hizo).
        assertThat(result.aggregationTriggered()).isFalse();
    }

    @Test
    void nonMvccBlockchainErrorOnAggregatePropagates() {
        // Un error que no es MVCC (p. ej. orderer caído) se propaga.
        when(blockchain.queryLeaderInfo(eq(roundId))).thenReturn(
            new LeaderInfo(false, 1, 1, List.of(myAnonCidHex)));

        BlockchainUnavailableException otherEx = new BlockchainUnavailableException(
            new RuntimeException("orderer down"));
        when(blockchain.tryAggregateRound(eq(roundId))).thenThrow(otherEx);
        when(errorTranslator.isMvccReadConflict(otherEx)).thenReturn(false);

        assertThatThrownBy(() -> newService().runRound(
            me, roundId, "logistic_regression", 2, Path.of("/data"), Map.of()))
            .isInstanceOf(BlockchainUnavailableException.class);
    }

    @Test
    void alreadyAggregatedExitsLoopGracefully() {
        // Este cliente es líder, llama a agg y el chaincode responde
        // "already_aggregated" (otro cliente ya agregó): sale limpio.
        when(blockchain.queryLeaderInfo(eq(roundId))).thenReturn(
            new LeaderInfo(false, 1, 1, List.of(myAnonCidHex)));

        when(blockchain.tryAggregateRound(eq(roundId)))
            .thenThrow(new AlreadyAggregatedException(roundId.value()));

        RoundResult result = newService().runRound(
            me, roundId, "logistic_regression", 2, Path.of("/data"), Map.of());

        assertThat(result.aggregationTriggered()).isFalse();
    }

    @Test
    void waitsWhileRoundNotReadyThenAggregatesWhenReady() {
        // Primer poll: faltan submits. Segundo: ronda lista y soy líder.
        when(blockchain.queryLeaderInfo(eq(roundId)))
            .thenReturn(new LeaderInfo(false, 0, 2, List.of(myAnonCidHex)))  // count<K, espero
            .thenReturn(new LeaderInfo(false, 2, 2, List.of(myAnonCidHex, "ff".repeat(65))));  // ronda lista

        String tallyHex = "f".repeat(64);
        byte[] modelBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.11f).putFloat(0.21f).array();
        GlobalModel newModel = GlobalModel.of(modelBytes, RoundId.of(2), TallyHash.of(tallyHex));
        AggregationResult agg = new AggregationResult(
            newModel, TallyHash.of(tallyHex), 2,
            new AggregationResult.ChaincodeTimings(
                Duration.ofMillis(1), Duration.ofMillis(2),
                Duration.ofMillis(3), Duration.ofMillis(10)));
        when(blockchain.tryAggregateRound(eq(roundId))).thenReturn(agg);

        RoundResult result = newService().runRound(
            me, roundId, "logistic_regression", 2, Path.of("/data"), Map.of());

        verify(blockchain).tryAggregateRound(eq(roundId));
        assertThat(result.aggregationTriggered()).isTrue();
    }
}
