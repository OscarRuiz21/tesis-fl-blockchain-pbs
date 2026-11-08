package org.fl.client.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StdoutMetricsParserTest {

    private final StdoutMetricsParser parser = new StdoutMetricsParser();

    @Test
    void parsesV3FormatLine() {
        String stdout = "accuracy=0.823456 loss=0.456789 num_samples=1024";

        var parsed = parser.parse(stdout);

        assertThat(parsed.accuracy()).isEqualTo(0.823456);
        assertThat(parsed.loss()).isEqualTo(0.456789);
        assertThat(parsed.numSamples()).isEqualTo(1024);
    }

    @Test
    void parsesLastLineWhenMultipleOutputLines() {
        String stdout = """
            Loading data...
            Training epoch 1/5
            Training epoch 2/5
            accuracy=0.987 loss=0.123 num_samples=500
            """;

        var parsed = parser.parse(stdout);

        assertThat(parsed.accuracy()).isEqualTo(0.987);
        assertThat(parsed.loss()).isEqualTo(0.123);
        assertThat(parsed.numSamples()).isEqualTo(500);
    }

    @Test
    void rejectsV2Format() {
        // Un formato con prefijo "OK" y sin num_samples debe fallar.
        String v2Stdout = "OK loss=0.456789 accuracy=0.823456";

        assertThatThrownBy(() -> parser.parse(v2Stdout))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingMetrics() {
        assertThatThrownBy(() -> parser.parse("FAIL: no metrics"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
