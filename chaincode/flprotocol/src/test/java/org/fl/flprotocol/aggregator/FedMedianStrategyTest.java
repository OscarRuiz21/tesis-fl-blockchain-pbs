package org.fl.flprotocol.aggregator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FedMedianStrategyTest {

    private final FedMedianStrategy strategy = new FedMedianStrategy();

    @Test
    void oddKReturnsMiddle() {
        // K=3, 1 dim: median(1, 5, 100) = 5
        float[] result = strategy.apply(
            new float[]{0.0f},
            List.of(
                new float[]{1.0f},
                new float[]{5.0f},
                new float[]{100.0f}
            )
        );

        assertArrayEquals(new float[]{5.0f}, result, 1e-6f);
    }

    @Test
    void evenKAveragesMiddleTwo() {
        // K=4, 1 dim: [1,3,7,9], mediana = (3+7)/2 = 5
        float[] result = strategy.apply(
            new float[]{0.0f},
            List.of(
                new float[]{1.0f},
                new float[]{9.0f},
                new float[]{3.0f},
                new float[]{7.0f}
            )
        );

        assertArrayEquals(new float[]{5.0f}, result, 1e-6f);
    }

    @Test
    void resistsOutliers() {
        // K=5: cuatro updates cerca de 1.0 y un outlier de 1000.0; la mediana
        // lo ignora y el resultado queda cerca de 1.0.
        float[] result = strategy.apply(
            new float[]{0.0f},
            List.of(
                new float[]{0.9f},
                new float[]{1.0f},
                new float[]{1.0f},
                new float[]{1.1f},
                new float[]{1000.0f}
            )
        );

        assertArrayEquals(new float[]{1.0f}, result, 1e-6f);
    }
}
