package org.fl.client.exception;

import org.fl.client.model.AnonId;
import org.fl.client.model.RoundId;

import java.util.Map;

/** El {@code anon_cid} ya está en el conjunto de nulificadores {@code N_r} de la ronda. */
public class DuplicateNullifierException extends ClientException {

    private final RoundId round;
    private final AnonId anonCid;

    public DuplicateNullifierException(RoundId round, AnonId anonCid) {
        super(
            "AnonCid already spent in round %d".formatted(round.value()),
            Map.of("round", round, "anonCid", anonCid)
        );
        this.round = round;
        this.anonCid = anonCid;
    }

    public RoundId getRound() { return round; }
    public AnonId getAnonCid() { return anonCid; }
}
