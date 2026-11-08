package org.fl.flprotocol.aggregator;

import java.util.List;

/**
 * Estrategia de agregación on-chain. {@link #name()} debe coincidir con
 * {@code aggregation_strategy} en el ledger y con {@code aggregation} en el YAML
 * del experimento.
 *
 * <p>Las implementaciones no guardan estado y deben ser deterministas bit a
 * bit: todos los peers que endosan tienen que producir el mismo modelo.</p>
 */
public interface AggregationStrategy {

    String name();

    /**
     * @param currentModel {@code w_r}; no se modifica.
     * @param updates      {@code Δw_r^k} en orden lexicográfico de claves del
     *                     ledger, el mismo en todos los peers.
     * @return {@code w_{r+1}}.
     */
    float[] apply(float[] currentModel, List<float[]> updates);
}
