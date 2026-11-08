package org.fl.coordinator.store;

import org.fl.coordinator.model.ClientMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Cuenta las rondas registradas en {@code <outputDir>/<label>/client-N.csv}.
 * Solo cuenta líneas, así que no depende de las columnas que escriba el cliente.
 */
@Component
public class FileMetricsReader {

    private static final Logger log = LoggerFactory.getLogger(FileMetricsReader.class);

    private final Path outputDir;

    public FileMetricsReader(@Value("${fl.results.output-dir}") String outputDir) {
        this.outputDir = Path.of(outputDir);
    }

    /** Un archivo ausente cuenta como cero rondas. */
    public List<ClientMetrics> readAll(String experimentLabel, int k) {
        Path dir = outputDir.resolve(experimentLabel);
        List<ClientMetrics> result = new ArrayList<>(k);
        for (int i = 1; i <= k; i++) {
            String clientLabel = "client-" + i;
            Path file = dir.resolve(clientLabel + ".csv");
            int rounds = countDataLines(file);
            result.add(new ClientMetrics(clientLabel, rounds));
        }
        return result;
    }

    private static int countDataLines(Path file) {
        if (!Files.exists(file)) {
            log.warn("Client metrics file missing: {}", file);
            return 0;
        }
        try (Stream<String> lines = Files.lines(file)) {
            // Sin contar la cabecera.
            long total = lines.count();
            return (int) Math.max(0, total - 1);
        } catch (IOException e) {
            log.warn("Failed to read {}", file, e);
            return 0;
        }
    }
}
