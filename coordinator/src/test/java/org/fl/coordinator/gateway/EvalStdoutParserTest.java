package org.fl.coordinator.gateway;

import org.fl.coordinator.exception.ModelEvaluationFailedException;
import org.fl.coordinator.model.EvaluationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalStdoutParserTest {

    private final EvalStdoutParser parser = new EvalStdoutParser();

    @Test
    void parsesSimpleAccuracyLossLine() {
        EvaluationResult r = parser.parse("accuracy=0.974 loss=0.082", Duration.ofSeconds(2));

        assertThat(r.accuracy()).isEqualTo(0.974);
        assertThat(r.loss()).isEqualTo(0.082);
        assertThat(r.evaluationTime()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void parsesLastLineWhenMultilineOutput() {
        String stdout = """
            Loading test set...
            Eval batch 1/100
            accuracy=0.5 loss=0.2
            """;

        EvaluationResult r = parser.parse(stdout, Duration.ofMillis(500));
        assertThat(r.accuracy()).isEqualTo(0.5);
        assertThat(r.loss()).isEqualTo(0.2);
    }

    @Test
    void rejectsMissingMetrics() {
        assertThatThrownBy(() -> parser.parse("FAIL: no metrics", Duration.ZERO))
            .isInstanceOf(ModelEvaluationFailedException.class);
        assertThatThrownBy(() -> parser.parse("", Duration.ZERO))
            .isInstanceOf(ModelEvaluationFailedException.class);
    }
}
