package org.fl.client.gateway;

import org.fl.client.model.AnonId;
import org.fl.client.model.Credential;
import org.fl.client.model.ModelUpdate;
import org.fl.client.model.RoundId;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Argumentos de {@code submitUpdate}, en el orden exacto del chaincode. El
 * nulificador no viaja aparte: el chaincode lo obtiene de {@code anonCidB64}.
 */
@Component
public class SubmitPayloadMapper {

    public StringPayload toStrings(
            AnonId anonCid, Credential credential, ModelUpdate update,
            byte[] sigModel, RoundId roundId) {
        return new StringPayload(
            anonCid.toBase64(),
            credential.rhoB64(),
            credential.omegaB64(),
            credential.sigmaB64(),
            credential.deltaB64(),
            Base64.getEncoder().encodeToString(update.bytes()),
            Base64.getEncoder().encodeToString(sigModel),
            String.valueOf(roundId.value())
        );
    }

    public record StringPayload(
        String anonCidB64,
        String rhoB64,
        String omegaB64,
        String sigmaB64,
        String deltaB64,
        String updateB64,
        String sigModelB64,
        String roundIdStr
    ) {}
}
