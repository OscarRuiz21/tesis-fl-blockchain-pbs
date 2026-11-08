package org.fl.client.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Etiqueta del experimento, asignada en {@code POST /lifecycle/start} igual
 * que {@link ClientBootstrap}; {@link FileMetricsStore} la usa como
 * subdirectorio de salida.
 */
@Component
public class ExperimentBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ExperimentBootstrap.class);

    private final AtomicReference<String> label = new AtomicReference<>();

    public void assign(String experimentLabel) {
        Objects.requireNonNull(experimentLabel, "experimentLabel must not be null");
        String prev = label.compareAndExchange(null, experimentLabel);
        if (prev == null) {
            log.info("Experiment label assigned by orchestrator: {}", experimentLabel);
            return;
        }
        if (!prev.equals(experimentLabel)) {
            throw new IllegalStateException(
                "Experiment label already assigned to '" + prev
                + "', cannot reassign to '" + experimentLabel + "'");
        }
    }

    public String label() {
        String v = label.get();
        if (v == null) {
            throw new IllegalStateException(
                "Experiment label not yet assigned by orchestrator (call /lifecycle/start first)");
        }
        return v;
    }

    public String labelOrNull() {
        return label.get();
    }
}
