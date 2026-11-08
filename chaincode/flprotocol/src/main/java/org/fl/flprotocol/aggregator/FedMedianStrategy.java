package org.fl.flprotocol.aggregator;

import java.util.Arrays;
import java.util.List;

/**
 * Mediana por coordenada (Yin et al., 2018):
 * <pre>
 *   w_{r+1}[i]  =  w_r[i] + median(Δw_r^1[i], ..., Δw_r^K[i])
 * </pre>
 *
 * <p>Con K par se promedian los dos valores centrales. Como cada columna se
 * ordena, el resultado no depende del orden de los updates.</p>
 */
public final class FedMedianStrategy implements AggregationStrategy {

    public static final String NAME = "fedmedian";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public float[] apply(float[] currentModel, List<float[]> updates) {
        int K = updates.size();
        int D = currentModel.length;
        float[] result = Arrays.copyOf(currentModel, D);
        float[] column = new float[K];

        for (int i = 0; i < D; i++) {
            for (int k = 0; k < K; k++) {
                column[k] = updates.get(k)[i];
            }
            Arrays.sort(column);
            float median;
            if (K % 2 == 1) {
                median = column[K / 2];
            } else {
                median = (column[K / 2 - 1] + column[K / 2]) / 2.0f;
            }
            result[i] += median;
        }
        return result;
    }
}
