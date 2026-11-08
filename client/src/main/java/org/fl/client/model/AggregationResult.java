package org.fl.client.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Resultado de {@code aggregateRound} visto por el líder: el modelo global de
 * la ronda r+1, el Tally Hash y los tiempos internos del chaincode.
 */
public record AggregationResult(
    GlobalModel newGlobalModel,
    TallyHash tally,
    int updatesUsed,
    ChaincodeTimings timings
) {
    public AggregationResult {
        Objects.requireNonNull(newGlobalModel, "newGlobalModel must not be null");
        Objects.requireNonNull(tally, "tally must not be null");
        Objects.requireNonNull(timings, "timings must not be null");
        if (updatesUsed < 1) {
            throw new IllegalArgumentException(
                "updatesUsed must be >= 1, got " + updatesUsed);
        }
    }

    public record ChaincodeTimings(
        Duration rangeQuery,
        Duration aggregation,
        Duration tally,
        Duration total
    ) {
        public ChaincodeTimings {
            Objects.requireNonNull(rangeQuery, "rangeQuery must not be null");
            Objects.requireNonNull(aggregation, "aggregation must not be null");
            Objects.requireNonNull(tally, "tally must not be null");
            Objects.requireNonNull(total, "total must not be null");
        }
    }
}
