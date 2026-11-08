package org.fl.as.model;

import java.util.Objects;

/**
 * Constancia de que el AS ya emitió credencial a un cliente en una ronda. Es
 * la primera barrera contra Sybil, previa incluso al commitment; la segunda es
 * el registro on-chain de los {@code anon_cid} consumidos, que funcionan como
 * nulificadores.
 */
public record IssuanceRecord(
    ClientId clientId,
    RoundId roundId
) {
    public IssuanceRecord {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(roundId, "roundId must not be null");
    }

    public String compositeKey() {
        return clientId.value() + "#" + roundId.value();
    }
}
