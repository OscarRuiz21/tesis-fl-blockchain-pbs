package org.fl.client.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fl.client.model.GlobalModel;
import org.fl.client.model.RoundId;
import org.fl.client.model.TallyHash;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

/** Respuesta de {@code queryGlobalModel}; un {@code model_hash} vacío queda como null. */
@Component
public class GlobalModelJsonMapper {

    private final ObjectMapper jackson = new ObjectMapper();

    public GlobalModel fromJson(String json, RoundId requestedRound) throws IOException {
        JsonNode root = jackson.readTree(json);
        String modelB64 = root.path("model").asText();
        String modelHashHex = root.path("model_hash").asText("");
        String tallyHex = root.path("tally_hash").asText("");
        long round = Long.parseLong(root.path("round").asText());

        byte[] bytes = Base64.getDecoder().decode(modelB64);
        TallyHash previousTally = (round == 1 || tallyHex.isEmpty())
            ? null
            : TallyHash.of(tallyHex);
        String publishedModelHash = modelHashHex.isEmpty() ? null : modelHashHex;

        return GlobalModel.of(bytes, RoundId.of(round), previousTally, publishedModelHash);
    }
}
