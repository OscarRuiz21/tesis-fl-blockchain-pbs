package org.fl.flprotocol.aggregator;

import java.util.Arrays;
import java.util.List;

/**
 * FedAvg (McMahan et al., 2017):
 * <pre>
 *   w_{r+1}[i]  =  w_r[i] + (1/K) · Σ_k Δw_r^k[i]
 * </pre>
 *
 * <p>Pesos uniformes {@code 1/K}: ponderar por el tamaño del dataset local
 * obligaría al cliente a revelarlo.</p>
 *
 * <p>La suma en float32 no es asociativa, así que el resultado idéntico entre
 * peers depende de que {@code readAllUpdates} entregue siempre el mismo orden
 * lexicográfico de claves.</p>
 */
public final class FedAvgStrategy implements AggregationStrategy {

    public static final String NAME = "fedavg";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public float[] apply(float[] currentModel, List<float[]> updates) {
        int K = updates.size();
        int D = currentModel.length;
        float[] result = Arrays.copyOf(currentModel, D);
        for (float[] update : updates) {
            for (int i = 0; i < D; i++) {
                result[i] += update[i] / K;
            }
        }
        return result;
    }
}
