package org.fl.coordinator.store;

import org.fl.coordinator.model.GlobalEvalRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Agrega una fila a {@code <outputDir>/<label>/global_eval.csv} por cada ronda evaluada. */
@Component
public class GlobalEvalCsvWriter {

    private static final String HEADER =
        "experiment,round_id,accuracy,loss,eval_time_s,timestamp";

    private final Path outputDir;

    public GlobalEvalCsvWriter(@Value("${fl.results.output-dir}") String outputDir) {
        this.outputDir = Path.of(outputDir);
    }

    public void append(String experimentLabel, GlobalEvalRecord record) {
        Path file = outputDir.resolve(experimentLabel).resolve("global_eval.csv");
        try {
            Files.createDirectories(file.getParent());
            boolean newFile = !Files.exists(file);
            if (newFile) {
                Files.writeString(file, HEADER + System.lineSeparator(),
                    StandardOpenOption.CREATE);
            }
            String line = String.format(
                "%s,%d,%.6f,%.6f,%.3f,%s%n",
                experimentLabel,
                record.roundId().value(),
                record.accuracy(),
                record.loss(),
                record.evaluationTime().toMillis() / 1000.0,
                record.timestamp().toString());
            Files.writeString(file, line, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
