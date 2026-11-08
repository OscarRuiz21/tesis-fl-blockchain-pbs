package org.fl.client.gateway;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee la última línea de stdout de {@code train.py},
 * {@code accuracy=X.XXXX loss=Y.YYYY num_samples=N}. Cualquier otro formato
 * es un error, para que una divergencia entre Java y Python no pase
 * desapercibida.
 */
@Component
public class StdoutMetricsParser {

    private static final Pattern ACC_PATTERN = Pattern.compile("accuracy=(-?[0-9]+\\.[0-9]+)");
    private static final Pattern LOSS_PATTERN = Pattern.compile("loss=(-?[0-9]+\\.[0-9]+)");
    private static final Pattern NUM_SAMPLES_PATTERN = Pattern.compile("num_samples=([0-9]+)");

    public ParsedMetrics parse(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            throw new IllegalArgumentException("Empty Python stdout");
        }
        String[] lines = stdout.strip().split("\\R");
        String last = lines[lines.length - 1];

        Matcher accM = ACC_PATTERN.matcher(last);
        Matcher lossM = LOSS_PATTERN.matcher(last);
        Matcher samplesM = NUM_SAMPLES_PATTERN.matcher(last);

        double acc = accM.find() ? Double.parseDouble(accM.group(1)) : Double.NaN;
        double loss = lossM.find() ? Double.parseDouble(lossM.group(1)) : Double.NaN;
        Integer numSamples = samplesM.find() ? Integer.parseInt(samplesM.group(1)) : null;

        if (Double.isNaN(acc) || Double.isNaN(loss) || numSamples == null) {
            throw new IllegalArgumentException(
                "Cannot parse metrics from stdout last line: '" + last + "'");
        }
        return new ParsedMetrics(acc, loss, numSamples);
    }

    public record ParsedMetrics(double accuracy, double loss, int numSamples) {}
}
