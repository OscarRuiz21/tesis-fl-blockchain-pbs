package org.fl.coordinator.gateway;

import org.fl.coordinator.exception.ModelEvaluationFailedException;
import org.fl.coordinator.model.EvaluationResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee la última línea de la salida de {@code eval.py}: {@code accuracy=0.974 loss=0.082}. */
@Component
public class EvalStdoutParser {

    private static final Pattern PATTERN = Pattern.compile(
        "accuracy=(-?[0-9]+\\.[0-9]+)\\s+loss=(-?[0-9]+\\.[0-9]+)");

    public EvaluationResult parse(String stdout, Duration evalTime) {
        if (stdout == null || stdout.isBlank()) {
            throw new ModelEvaluationFailedException(0, "Empty eval stdout");
        }
        String[] lines = stdout.strip().split("\\R");
        String last = lines[lines.length - 1];
        Matcher m = PATTERN.matcher(last);
        if (!m.find()) {
            throw new ModelEvaluationFailedException(
                0, "No accuracy/loss in stdout last line: '" + last + "'");
        }
        double accuracy = Double.parseDouble(m.group(1));
        double loss = Double.parseDouble(m.group(2));
        return new EvaluationResult(accuracy, loss, evalTime);
    }
}
