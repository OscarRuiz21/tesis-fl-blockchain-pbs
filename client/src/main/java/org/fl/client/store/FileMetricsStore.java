package org.fl.client.store;

import org.fl.client.model.AggregationResult;
import org.fl.client.model.AuditResult;
import org.fl.client.model.ClientId;
import org.fl.client.model.PhaseTimings;
import org.fl.client.model.RoundId;
import org.fl.client.model.SubmitResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Métricas por ronda en {@code <metricsOutputDir>/<experimentLabel>/<clientId>.csv}.
 * Cada ronda se acumula en memoria y se escribe en una sola línea al llegar
 * {@code recordRoundTotal}, para no dejar filas parciales.
 */
@Component
public class FileMetricsStore {

    private static final Logger log = LoggerFactory.getLogger(FileMetricsStore.class);

    private final ExperimentBootstrap experimentBootstrap;
    private final String metricsOutputDir;

    private final ConcurrentMap<RoundId, RoundBuffer> buffers = new ConcurrentHashMap<>();

    public FileMetricsStore(
            ExperimentBootstrap experimentBootstrap,
            @Value("${fl.metrics-output-dir}") String metricsOutputDir) {
        this.experimentBootstrap = experimentBootstrap;
        this.metricsOutputDir = metricsOutputDir;
    }

    /**
     * Borra los CSV de corridas previas con la misma etiqueta. Se llama solo al
     * arrancar el experimento, no en cada escritura. Si un archivo no se puede
     * borrar, se registra y se sigue; los duplicados quedan visibles en el
     * análisis.
     */
    public void resetForLabel(String label) {
        Path dir = Path.of(metricsOutputDir).resolve(label);
        if (!Files.isDirectory(dir)) {
            buffers.clear();
            return;
        }
        try (var stream = Files.list(dir)) {
            long deleted = stream
                .filter(p -> p.getFileName().toString().endsWith(".csv"))
                .mapToLong(p -> {
                    try {
                        Files.delete(p);
                        return 1L;
                    } catch (IOException e) {
                        log.warn("Could not delete stale CSV {}: {}", p, e.getMessage());
                        return 0L;
                    }
                })
                .sum();
            if (deleted > 0) {
                log.info("Reset {} stale CSV(s) for experiment label '{}'", deleted, label);
            }
        } catch (IOException e) {
            log.warn("Failed to enumerate metrics dir {}: {}", dir, e.getMessage());
        }
        buffers.clear();
    }

    public void recordPhase1Timing(RoundId round, ClientId client, PhaseTimings timings) {
        buffers.computeIfAbsent(round, k -> new RoundBuffer()).phase1 = timings;
    }

    public void recordQueryGlobalModel(RoundId round, ClientId client, Duration duration) {
        buffers.computeIfAbsent(round, k -> new RoundBuffer()).queryGlobalModel = duration;
    }

    /**
     * {@code submitDuration} incluye red, orderer y commit. Los intentos
     * ayudan a leerlo: 2300 ms con 5 intentos son reintentos MVCC; con 1
     * intento, un orderer lento.
     */
    public void recordPhase2(RoundId round, ClientId client, Duration submitDuration,
                             SubmitResponse.SubmitTimings submitTimings, int attempts) {
        RoundBuffer buf = buffers.computeIfAbsent(round, k -> new RoundBuffer());
        buf.phase2 = submitDuration;
        buf.submitTimings = submitTimings;
        buf.submitAttempts = attempts;
    }

    public void recordPhase3Trigger(RoundId round, ClientId client, boolean triggered,
                                    Duration duration,
                                    AggregationResult.ChaincodeTimings chaincodeTimings) {
        RoundBuffer buf = buffers.computeIfAbsent(round, k -> new RoundBuffer());
        buf.phase3Triggered = triggered;
        buf.phase3 = duration;
        buf.chaincodeTimings = chaincodeTimings;
    }

    /**
     * {@code pollCount} incluye los sondeos fallidos; {@code pollDuration}
     * excluye las esperas entre sondeos. {@code leaderPosition} es -1 si el
     * cliente nunca vio la ronda completa.
     */
    public void recordPhase3Polling(RoundId round, ClientId client,
                                    int pollCount, Duration pollDuration,
                                    boolean wasPrimaryLeader, int leaderPosition) {
        RoundBuffer buf = buffers.computeIfAbsent(round, k -> new RoundBuffer());
        buf.phase3PollCount = pollCount;
        buf.phase3PollDuration = pollDuration;
        buf.phase3WasPrimaryLeader = wasPrimaryLeader;
        buf.phase3LeaderPosition = leaderPosition;
    }

    public void recordTraining(RoundId round, ClientId client, double loss, double accuracy,
                               int numSamples, Duration duration) {
        RoundBuffer buf = buffers.computeIfAbsent(round, k -> new RoundBuffer());
        buf.trainLoss = loss;
        buf.trainAccuracy = accuracy;
        buf.trainNumSamples = numSamples;
        buf.trainDuration = duration;
    }

    public void recordRoundTotal(RoundId round, ClientId client, Duration total) {
        RoundBuffer buf = buffers.remove(round);
        if (buf == null) {
            log.warn("recordRoundTotal called without prior records for round {}", round);
            return;
        }
        buf.roundWallTime = total;
        flush(round, client, buf);
    }

    /**
     * La auditoría va a {@code audit-<clientId>.csv}, aparte, para no alterar
     * las columnas del CSV principal entre configuraciones.
     */
    public void recordAudit(RoundId round, ClientId client, AuditResult audit) {
        Path dir = Path.of(metricsOutputDir).resolve(experimentBootstrap.label());
        Path file = dir.resolve("audit-" + client.value() + ".csv");
        try {
            Files.createDirectories(dir);
            boolean newFile = !Files.exists(file);
            if (newFile) {
                Files.writeString(file, auditCsvHeader() + System.lineSeparator(),
                    StandardOpenOption.CREATE);
            }
            Files.writeString(file, auditCsvLine(round, client, audit) + System.lineSeparator(),
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write audit CSV for round {} client {}",
                round.value(), client.value(), e);
        }
    }

    private static String auditCsvHeader() {
        return String.join(",",
            "round", "clientId", "status",
            "p4_included", "p5_tally_match",
            "model_hash_checked", "model_hash_match",
            "hash_count", "local_update_hash",
            "recomputed_tally", "published_tally");
    }

    private static String auditCsvLine(RoundId round, ClientId client, AuditResult a) {
        return String.join(",",
            String.valueOf(round.value()),
            client.value(),
            a.status().name(),
            String.valueOf(a.p4Included()),
            String.valueOf(a.p5TallyMatch()),
            String.valueOf(a.modelHashChecked()),
            String.valueOf(a.modelHashMatch()),
            String.valueOf(a.hashCount()),
            a.localUpdateHashHex() == null ? "" : a.localUpdateHashHex(),
            a.recomputedTallyHex() == null ? "" : a.recomputedTallyHex(),
            a.publishedTallyHex() == null ? "" : a.publishedTallyHex());
    }

    private void flush(RoundId round, ClientId client, RoundBuffer buf) {
        Path dir = Path.of(metricsOutputDir).resolve(experimentBootstrap.label());
        Path file = dir.resolve(client.value() + ".csv");
        try {
            Files.createDirectories(dir);
            boolean newFile = !Files.exists(file);
            String line = buf.toCsvLine(round, client) + System.lineSeparator();
            if (newFile) {
                Files.writeString(file, RoundBuffer.csvHeader() + System.lineSeparator(),
                    StandardOpenOption.CREATE);
            }
            Files.writeString(file, line, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to flush metrics for round {} client {}", round, client.value(), e);
        }
    }

    /** Solo lo toca el hilo del experimento. */
    private static final class RoundBuffer {
        PhaseTimings phase1;
        Duration queryGlobalModel;
        Duration phase2;
        SubmitResponse.SubmitTimings submitTimings;
        int submitAttempts = 1;
        boolean phase3Triggered;
        Duration phase3;
        AggregationResult.ChaincodeTimings chaincodeTimings;
        int phase3PollCount;
        Duration phase3PollDuration;
        boolean phase3WasPrimaryLeader;
        int phase3LeaderPosition = -1;

        double trainLoss;
        double trainAccuracy;
        int trainNumSamples;
        Duration trainDuration;
        Duration roundWallTime;

        static String csvHeader() {
            return String.join(",",
                "round", "clientId",
                "phase1_blind_ms", "phase1_unblind_ms", "phase1_verify_ms",
                "phase1_commit_rtt_ms", "phase1_sign_rtt_ms",
                "train_loss", "train_accuracy", "train_num_samples", "train_duration_ms",
                "phase2_submit_ms",
                "phase3_triggered", "phase3_duration_ms",
                "chaincode_range_query_ms", "chaincode_aggregation_ms",
                "chaincode_tally_ms", "chaincode_total_ms",
                "round_wall_time_ms",
                "phase3_poll_count", "phase3_poll_ms",
                "phase3_was_leader", "phase3_leader_position",
                // Las columnas nuevas van al final para no desplazar las previas.
                "phase1_fetch_pubkey_rtt_ms",     // ~0 después de la ronda 1
                "query_global_model_ms",
                "submit_pbs_verify_ms",           // tiempos internos del chaincode
                "submit_sig_model_verify_ms",
                "submit_state_write_ms",
                "submit_chaincode_total_ms",
                "submit_attempts");
        }

        String toCsvLine(RoundId round, ClientId client) {
            return String.join(",",
                String.valueOf(round.value()),
                client.value(),
                ms(phase1 == null ? Duration.ZERO : phase1.blindMs()),
                ms(phase1 == null ? Duration.ZERO : phase1.unblindMs()),
                ms(phase1 == null ? Duration.ZERO : phase1.verifyLocalMs()),
                ms(phase1 == null ? Duration.ZERO : phase1.commitRttMs()),
                ms(phase1 == null ? Duration.ZERO : phase1.signRttMs()),
                String.valueOf(trainLoss),
                String.valueOf(trainAccuracy),
                String.valueOf(trainNumSamples),
                ms(trainDuration),
                ms(phase2),
                String.valueOf(phase3Triggered),
                ms(phase3),
                ms(chaincodeTimings == null ? Duration.ZERO : chaincodeTimings.rangeQuery()),
                ms(chaincodeTimings == null ? Duration.ZERO : chaincodeTimings.aggregation()),
                ms(chaincodeTimings == null ? Duration.ZERO : chaincodeTimings.tally()),
                ms(chaincodeTimings == null ? Duration.ZERO : chaincodeTimings.total()),
                ms(roundWallTime),
                String.valueOf(phase3PollCount),
                ms(phase3PollDuration),
                String.valueOf(phase3WasPrimaryLeader),
                String.valueOf(phase3LeaderPosition),
                ms(phase1 == null ? Duration.ZERO : phase1.fetchPubKeyRttMs()),
                ms(queryGlobalModel),
                ms(submitTimings == null ? Duration.ZERO : submitTimings.pbsVerify()),
                ms(submitTimings == null ? Duration.ZERO : submitTimings.sigModelVerify()),
                ms(submitTimings == null ? Duration.ZERO : submitTimings.stateWrite()),
                ms(submitTimings == null ? Duration.ZERO : submitTimings.total()),
                String.valueOf(submitAttempts));
        }

        /**
         * Milisegundos con tres decimales: {@link Duration#toMillis()} truncaba
         * a 0 operaciones de menos de 1 ms, como el descegado.
         */
        private static String ms(Duration d) {
            if (d == null) return "0";
            return String.format(Locale.ROOT, "%.3f", d.toNanos() / 1_000_000.0);
        }
    }
}
