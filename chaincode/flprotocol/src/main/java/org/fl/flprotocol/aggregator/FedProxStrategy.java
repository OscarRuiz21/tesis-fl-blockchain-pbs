package org.fl.flprotocol.aggregator;

import java.util.List;

/**
 * FedProx (Li et al., 2020). En la agregación es igual a FedAvg; el término
 * proximal {@code (μ/2)·||w - w_r||²} vive en el entrenamiento local del
 * cliente. Existe como clase aparte para que la estrategia quede registrada con
 * su nombre en el ledger y en las métricas.
 */
public final class FedProxStrategy implements AggregationStrategy {

    public static final String NAME = "fedprox";

    private final FedAvgStrategy fedAvg = new FedAvgStrategy();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public float[] apply(float[] currentModel, List<float[]> updates) {
        return fedAvg.apply(currentModel, updates);
    }
}
