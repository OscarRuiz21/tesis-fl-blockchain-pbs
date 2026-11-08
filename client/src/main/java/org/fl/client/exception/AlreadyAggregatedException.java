package org.fl.client.exception;

import java.util.Map;

/**
 * El chaincode respondió {@code already_aggregated}: otro candidato a líder
 * agregó primero. No es una falla; {@code RunRoundService} sale del sondeo
 * con {@code triggered=false}.
 */
public final class AlreadyAggregatedException extends ClientException {

    public AlreadyAggregatedException(long round) {
        super("Round " + round + " was already aggregated by another client",
            Map.of("round", round));
    }
}
