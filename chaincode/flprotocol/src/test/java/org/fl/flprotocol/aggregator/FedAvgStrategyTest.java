package org.fl.flprotocol.aggregator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FedAvgStrategyTest {

    private final FedAvgStrategy strategy = new FedAvgStrategy();

    @Test
    void singleClientReturnsDelta() {
        // K=1: result = current + delta
        float[] result = strategy.apply(
            new float[]{1.0f, 2.0f, 3.0f},
            List.of(new float[]{0.1f, 0.2f, 0.3f})
        );

        assertArrayEquals(new float[]{1.1f, 2.2f, 3.3f}, result, 1e-6f);
    }

    @Test
    void multipleClientsAverage() {
        // K=3: result[i] = current[i] + (Σ updates[k][i]) / 3
        float[] result = strategy.apply(
            new float[]{0.0f, 0.0f},
            List.of(
                new float[]{1.0f, 2.0f},
                new float[]{2.0f, 4.0f},
                new float[]{3.0f, 6.0f}
            )
        );

        // Promedio: ((1+2+3)/3, (2+4+6)/3) = (2.0, 4.0)
        assertArrayEquals(new float[]{2.0f, 4.0f}, result, 1e-6f);
    }

    @Test
    void preservesDimension() {
        float[] result = strategy.apply(
            new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f},
            List.of(new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f})
        );

        assertEquals(5, result.length);
    }
}
