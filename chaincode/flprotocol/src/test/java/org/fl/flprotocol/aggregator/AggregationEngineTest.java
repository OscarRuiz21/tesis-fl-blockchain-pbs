package org.fl.flprotocol.aggregator;

import org.fl.flprotocol.storage.ErrorCodes;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AggregationEngineTest {

    private final AggregationEngine engine = new AggregationEngine();

    @Test
    void dispatchesToCorrectStrategy() {
        // K=3 con un outlier: FedAvg y FedMedian dan resultados distintos.
        float[] currentModel = new float[]{0.0f};
        var updates = List.of(
            new float[]{1.0f},
            new float[]{1.0f},
            new float[]{100.0f}
        );

        float[] avg = engine.aggregate("fedavg", currentModel, updates);
        float[] median = engine.aggregate("fedmedian", currentModel, updates);

        // FedAvg: (1+1+100)/3 = 34.0; FedMedian: 1.0
        assertEquals(34.0f, avg[0], 1e-5f);
        assertEquals(1.0f, median[0], 1e-5f);
        assertNotEquals(avg[0], median[0]);

        // FedProx agrega igual que FedAvg; el término proximal está en Python.
        float[] prox = engine.aggregate("fedprox", currentModel, updates);
        assertArrayEquals(avg, prox, 1e-6f);
    }

    @Test
    void unknownStrategyThrows() {
        ChaincodeException ex = assertThrows(ChaincodeException.class,
            () -> engine.aggregate("unknown", new float[]{0.0f}, List.of(new float[]{1.0f})));
        assertTrue(ex.getMessage().startsWith(ErrorCodes.UNKNOWN_STRATEGY + ":"));
    }

    @Test
    void defaultConstructorRegistersThreeBuiltinStrategies() {
        assertEquals(java.util.Set.of("fedavg", "fedmedian", "fedprox"),
            engine.registeredStrategyNames());
        assertTrue(engine.isKnownStrategy("fedavg"));
        assertFalse(engine.isKnownStrategy("fedNova"));
    }

    @Test
    void customStrategyCanBeRegistered() {
        AggregationStrategy fedSum = new AggregationStrategy() {
            @Override public String name() { return "fedsum"; }
            @Override public float[] apply(float[] currentModel, List<float[]> updates) {
                float[] result = currentModel.clone();
                for (float[] u : updates) {
                    for (int i = 0; i < result.length; i++) result[i] += u[i];
                }
                return result;
            }
        };

        AggregationEngine custom = new AggregationEngine(List.of(
            new FedAvgStrategy(),
            fedSum
        ));

        assertTrue(custom.isKnownStrategy("fedsum"));
        assertFalse(custom.isKnownStrategy("fedmedian"));  // no la registramos

        float[] result = custom.aggregate("fedsum",
            new float[]{0.0f, 0.0f},
            List.of(new float[]{1.0f, 2.0f}, new float[]{3.0f, 4.0f}));
        assertArrayEquals(new float[]{4.0f, 6.0f}, result, 1e-6f);
    }

    @Test
    void duplicateStrategyNamesRejected() {
        AggregationStrategy duplicate = new AggregationStrategy() {
            @Override public String name() { return "fedavg"; }
            @Override public float[] apply(float[] m, List<float[]> u) { return m; }
        };

        assertThrows(IllegalArgumentException.class,
            () -> new AggregationEngine(List.of(new FedAvgStrategy(), duplicate)));
    }

    @Test
    void emptyStrategyListRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new AggregationEngine(List.of()));
    }
}
