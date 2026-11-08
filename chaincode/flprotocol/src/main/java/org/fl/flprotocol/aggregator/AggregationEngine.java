package org.fl.flprotocol.aggregator;

import org.fl.flprotocol.storage.ErrorCodes;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Selecciona la estrategia de agregación por el nombre guardado en el ledger. */
public final class AggregationEngine {

    private final Map<String, AggregationStrategy> strategies;

    public AggregationEngine() {
        this(List.of(
            new FedAvgStrategy(),
            new FedMedianStrategy(),
            new FedProxStrategy()
        ));
    }

    /** @throws IllegalArgumentException si la lista está vacía o repite nombres. */
    public AggregationEngine(List<AggregationStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("strategies must be non-empty");
        }
        this.strategies = strategies.stream()
            .collect(Collectors.toUnmodifiableMap(
                AggregationStrategy::name,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalArgumentException(
                        "Duplicate aggregation strategy name: " + a.name());
                }));
    }

    /** @throws org.hyperledger.fabric.shim.ChaincodeException {@code UNKNOWN_STRATEGY} */
    public float[] aggregate(String name, float[] currentModel, List<float[]> updates) {
        AggregationStrategy strategy = strategies.get(name);
        if (strategy == null) {
            throw ErrorCodes.unknownStrategy(name);
        }
        return strategy.apply(currentModel, updates);
    }

    public boolean isKnownStrategy(String name) {
        return strategies.containsKey(name);
    }

    public Set<String> registeredStrategyNames() {
        return strategies.keySet();
    }
}
