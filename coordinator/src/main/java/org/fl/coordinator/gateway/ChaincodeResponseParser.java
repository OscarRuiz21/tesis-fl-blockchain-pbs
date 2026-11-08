package org.fl.coordinator.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fl.coordinator.model.RoundId;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Lee la respuesta JSON de {@code queryRoundStatus}. */
@Component
public class ChaincodeResponseParser {

    private final ObjectMapper jackson = new ObjectMapper();

    public RoundStatusJson parseRoundStatusResponse(String json) throws IOException {
        JsonNode root = jackson.readTree(json);
        return new RoundStatusJson(
            RoundId.of(root.path("active_round").asLong()),
            root.path("total_clients").asInt(),
            root.path("strategy").asText());
    }

    public record RoundStatusJson(RoundId activeRound, int totalClients, String strategyName) {}
}
