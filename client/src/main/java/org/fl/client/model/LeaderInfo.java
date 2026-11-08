package org.fl.client.model;

import java.util.List;
import java.util.Objects;

/**
 * Estado de la ronda para elegir al líder de la Etapa 3. Se obtiene con
 * {@code evaluateTransaction}, así que sus range reads no entran a ningún
 * read-set validado por el orderer.
 *
 * <p>Con la ronda completa, el líder es el {@code anon_cid} lexicográficamente
 * menor ({@code leaderAt(0)}). Si no agrega a tiempo, el turno pasa a
 * {@code leaderAt(1)}, {@code leaderAt(2)}, etc.; la idempotencia de
 * {@code aggregateRound} hace inofensivo que dos candidatos disparen a la vez.</p>
 *
 * <p>El orden cambia de forma pseudoaleatoria entre rondas porque cada
 * {@code anon_cid} depende de {@code H(master_sk ∥ r)}, de modo que el
 * liderazgo no vincula rondas con clientes reales.</p>
 */
public record LeaderInfo(
    boolean modelAggregated,
    int updatesCount,
    int expectedClients,
    List<String> anonCidsSorted
) {
    public LeaderInfo {
        Objects.requireNonNull(anonCidsSorted, "anonCidsSorted must not be null");
        if (updatesCount < 0) {
            throw new IllegalArgumentException("updatesCount must be >= 0");
        }
        if (expectedClients < 1) {
            throw new IllegalArgumentException("expectedClients must be >= 1");
        }
        anonCidsSorted = List.copyOf(anonCidsSorted);
    }

    public boolean isRoundReady() {
        return updatesCount >= expectedClients;
    }

    public String leaderAt(int idx) {
        if (idx < 0 || idx >= anonCidsSorted.size()) {
            throw new IndexOutOfBoundsException(
                "leader index " + idx + " out of range [0, " + anonCidsSorted.size() + ")");
        }
        return anonCidsSorted.get(idx);
    }

    /** -1 si el {@code anonCidHex} no está registrado en la ronda. */
    public int positionOf(String anonCidHex) {
        return anonCidsSorted.indexOf(anonCidHex);
    }
}
