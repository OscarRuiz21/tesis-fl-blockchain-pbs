package org.fl.coordinator.store;

import org.fl.coordinator.model.ClientMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileMetricsReaderTest {

    @Test
    void readsExistingCsvAndCountsRoundLines(@TempDir Path tmp) throws IOException {
        String label = "smoke_run";
        Path expDir = Files.createDirectories(tmp.resolve(label));
        Files.writeString(expDir.resolve("client-1.csv"),
            "round,accuracy,loss\n1,0.5,0.4\n2,0.6,0.3\n3,0.7,0.2\n");
        Files.writeString(expDir.resolve("client-2.csv"),
            "round,accuracy,loss\n1,0.4,0.5\n");

        FileMetricsReader reader = new FileMetricsReader(tmp.toString());
        List<ClientMetrics> metrics = reader.readAll(label, 2);

        assertThat(metrics).hasSize(2);
        assertThat(metrics.get(0).recordedRounds()).isEqualTo(3);
        assertThat(metrics.get(1).recordedRounds()).isEqualTo(1);
    }

    @Test
    void zeroRoundsForMissingClientFiles(@TempDir Path tmp) {
        FileMetricsReader reader = new FileMetricsReader(tmp.toString());
        List<ClientMetrics> metrics = reader.readAll("never_ran", 3);

        assertThat(metrics).hasSize(3);
        assertThat(metrics).allMatch(m -> m.recordedRounds() == 0);
    }

    @Test
    void clientLabelsFollowBaseOneConvention(@TempDir Path tmp) {
        FileMetricsReader reader = new FileMetricsReader(tmp.toString());
        List<ClientMetrics> metrics = reader.readAll("any", 4);

        assertThat(metrics).extracting(ClientMetrics::clientId)
            .containsExactly("client-1", "client-2", "client-3", "client-4");
    }
}
