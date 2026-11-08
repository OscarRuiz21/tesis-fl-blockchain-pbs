package org.fl.client.exception;

import org.fl.client.model.AnonId;
import org.fl.client.model.RoundId;

import java.util.Map;

/** El chaincode rechazó la credencial PBS o {@code σ_model}: no la emitió el AS o fue alterada. */
public class InvalidPBSSignatureException extends ClientException {

    private final AnonId anonCid;
    private final RoundId round;

    public InvalidPBSSignatureException(AnonId anonCid, RoundId round) {
        super(
            "Invalid PBS signature for anonCid=%s... round=%d"
                .formatted(anonCid.toUncompressedHex().substring(0, 16), round.value()),
            Map.of("anonCid", anonCid, "round", round)
        );
        this.anonCid = anonCid;
        this.round = round;
    }

    public AnonId getAnonCid() { return anonCid; }
    public RoundId getRound() { return round; }
}
