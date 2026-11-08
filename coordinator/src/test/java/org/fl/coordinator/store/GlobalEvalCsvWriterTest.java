package org.fl.coordinator.store;

import org.fl.coordinator.model.GlobalEvalRecord;
import org.fl.coordinator.model.RoundId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalEvalCsvWriterTest {

    @Test
    void appendsHeaderAndDataLine(@TempDir Path tmp) throws IOException {
        GlobalEvalCsvWriter writer = new GlobalEvalCsvWriter(tmp.toString());
        GlobalEvalRecord record = new GlobalEvalRecord(
            RoundId.of(1), 0.75, 0.25,
            Duration.ofSeconds(2),
            Instant.parse("2026-04-27T10:00:00Z"));

        writer.append("test-exp", record);

        Path file = tmp.resolve("test-exp/global_eval.csv");
        assertThat(Files.exists(file)).isTrue();
        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).startsWith("experiment,round_id,accuracy,loss");
        assertThat(lines.get(1)).startsWith("test-exp,1,0.750000,0.250000");
    }

    @Test
    void appendsMultipleRoundsWithSingleHeader(@TempDir Path tmp) throws IOException {
        GlobalEvalCsvWriter writer = new GlobalEvalCsvWriter(tmp.toString());

        for (int r = 1; r <= 3; r++) {
            writer.append("exp-multi", new GlobalEvalRecord(
                RoundId.of(r), 0.5 + 0.1 * r, 0.5 - 0.1 * r,
                Duration.ofSeconds(1),
                Instant.parse("2026-04-27T10:00:0" + r + "Z")));
        }

        Path file = tmp.resolve("exp-multi/global_eval.csv");
        List<String> lines = Files.readAllLines(file);
        // 1 header + 3 datos.
        assertThat(lines).hasSize(4);
    }
}
