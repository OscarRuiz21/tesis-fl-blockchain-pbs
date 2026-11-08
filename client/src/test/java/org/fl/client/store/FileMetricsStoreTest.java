package org.fl.client.store;

import org.fl.client.model.ClientId;
import org.fl.client.model.PhaseTimings;
import org.fl.client.model.RoundId;
import org.fl.client.model.SubmitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tras resetForLabel, una nueva corrida con la misma etiqueta escribe sus
 * filas sin mezclarse con las de la anterior.
 */
class FileMetricsStoreTest {

    @TempDir
    Path tmpDir;

    private ExperimentBootstrap bootstrap;
    private FileMetricsStore store;
    private ClientId client;

    @BeforeEach
    void setUp() {
        bootstrap = mock(ExperimentBootstrap.class);
        when(bootstrap.label()).thenReturn("test-label");
        store = new FileMetricsStore(bootstrap, tmpDir.toString());
        client = ClientId.of("client-1");
    }

    @Test
    void writingTwoRoundsProducesHeaderPlusTwoDataLines() throws IOException {
        flushRound(RoundId.of(1));
        flushRound(RoundId.of(2));

        Path csv = tmpDir.resolve("test-label").resolve("client-1.csv");
        List<String> lines = Files.readAllLines(csv);
        assertThat(lines).hasSize(3); // header + 2 data
        assertThat(lines.get(0)).startsWith("round,clientId,");
        assertThat(lines.get(1)).startsWith("1,client-1,");
        assertThat(lines.get(2)).startsWith("2,client-1,");
    }

    @Test
    void resetForLabelDeletesExistingCsvs() throws IOException {
        flushRound(RoundId.of(1));
        Path csv = tmpDir.resolve("test-label").resolve("client-1.csv");
        assertThat(Files.exists(csv)).isTrue();

        store.resetForLabel("test-label");

        assertThat(Files.exists(csv)).isFalse();
        // Solo se borran los *.csv; el directorio queda.
        assertThat(Files.exists(csv.getParent())).isTrue();
    }

    @Test
    void writingAfterResetStartsFreshFile() throws IOException {
        flushRound(RoundId.of(1));
        flushRound(RoundId.of(2));
        store.resetForLabel("test-label");
        flushRound(RoundId.of(1));

        Path csv = tmpDir.resolve("test-label").resolve("client-1.csv");
        List<String> lines = Files.readAllLines(csv);
        // header + 1 fila de datos, no las 2 anteriores del primer batch.
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).startsWith("1,client-1,");
    }

    @Test
    void resetForLabelOnMissingDirIsNoop() {
        assertThatCode(() -> store.resetForLabel("doesnt-exist"))
            .doesNotThrowAnyException();
    }

    @Test
    void csvHeaderIncludesNewAuditColumns() throws IOException {
        flushRound(RoundId.of(1));
        Path csv = tmpDir.resolve("test-label").resolve("client-1.csv");
        String header = Files.readAllLines(csv).get(0);

        assertThat(header).contains("phase1_fetch_pubkey_rtt_ms");
        assertThat(header).contains("query_global_model_ms");
        assertThat(header).contains("submit_pbs_verify_ms");
        assertThat(header).contains("submit_sig_model_verify_ms");
        assertThat(header).contains("submit_state_write_ms");
        assertThat(header).contains("submit_chaincode_total_ms");
        assertThat(header).contains("submit_attempts");
    }

    @Test
    void durationsReportFractionalMillisecondPrecision() throws IOException {
        // 250 µs deben salir como 0.250 ms; toMillis() daba 0.
        store.recordPhase1Timing(RoundId.of(1), client, PhaseTimings.zero());
        store.recordQueryGlobalModel(RoundId.of(1), client, Duration.ofNanos(250_000));
        store.recordTraining(RoundId.of(1), client, 0.0, 0.0, 0, Duration.ZERO);
        SubmitResponse.SubmitTimings zeroTimings = new SubmitResponse.SubmitTimings(
            Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
        store.recordPhase2(RoundId.of(1), client, Duration.ZERO, zeroTimings, 1);
        store.recordPhase3Trigger(RoundId.of(1), client, false, Duration.ZERO, null);
        store.recordPhase3Polling(RoundId.of(1), client, 0, Duration.ZERO, false, -1);
        store.recordRoundTotal(RoundId.of(1), client, Duration.ZERO);

        Path csv = tmpDir.resolve("test-label").resolve("client-1.csv");
        String header = Files.readAllLines(csv).get(0);
        String dataLine = Files.readAllLines(csv).get(1);

        int colIdx = java.util.List.of(header.split(",")).indexOf("query_global_model_ms");
        String[] cells = dataLine.split(",");
        assertThat(cells[colIdx]).isEqualTo("0.250");
    }

    @Test
    void resetForLabelLeavesNonCsvFilesUntouched() throws IOException {
        flushRound(RoundId.of(1));
        Path labelDir = tmpDir.resolve("test-label");
        Path readme = labelDir.resolve("notes.txt");
        Files.writeString(readme, "some context");

        store.resetForLabel("test-label");

        assertThat(Files.exists(labelDir.resolve("client-1.csv"))).isFalse();
        assertThat(Files.exists(readme)).isTrue();
    }

    private void flushRound(RoundId round) {
        store.recordPhase1Timing(round, client, PhaseTimings.zero());
        store.recordQueryGlobalModel(round, client, Duration.ofMillis(5));
        store.recordTraining(round, client, 0.5, 0.8, 100, Duration.ofMillis(1000));
        SubmitResponse.SubmitTimings zeroTimings = new SubmitResponse.SubmitTimings(
            Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
        store.recordPhase2(round, client, Duration.ofMillis(50), zeroTimings, 1);
        store.recordPhase3Trigger(round, client, false, Duration.ZERO, null);
        store.recordPhase3Polling(round, client, 1, Duration.ofMillis(10), false, -1);
        store.recordRoundTotal(round, client, Duration.ofMillis(2000));
    }
}
