package org.fl.as.store;

import org.fl.as.model.ClientId;
import org.fl.as.model.IssuanceRecord;
import org.fl.as.model.RoundId;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Emisiones ya hechas por (cliente, ronda), primera barrera contra Sybil.
 * {@code putIfAbsent} garantiza que de dos registros concurrentes del mismo
 * par solo uno gane. No se limpia entre rondas.
 */
@Component
public class IssuanceLedger {

    private final ConcurrentMap<String, Boolean> records = new ConcurrentHashMap<>();

    /** {@code false} si ya había una emisión para ese (cliente, ronda). */
    public boolean tryRecord(IssuanceRecord record) {
        return records.putIfAbsent(record.compositeKey(), Boolean.TRUE) == null;
    }

    public boolean contains(ClientId clientId, RoundId roundId) {
        return records.containsKey(new IssuanceRecord(clientId, roundId).compositeKey());
    }
}
