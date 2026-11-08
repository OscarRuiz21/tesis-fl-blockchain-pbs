package org.fl.client.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fl.client.model.AggregationResult;
import org.fl.client.model.GlobalModel;
import org.fl.client.model.LeaderInfo;
import org.fl.client.model.RoundId;
import org.fl.client.model.RoundStatus;
import org.fl.client.model.SubmitResponse;
import org.fl.client.model.TallyHash;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Lee las respuestas JSON del chaincode, que incluyen sus tiempos internos. */
@Component
public class ChaincodeResponseParser {

    private final ObjectMapper jackson = new ObjectMapper();

    public SubmitResponse parseSubmitResponse(String json) throws IOException {
        JsonNode root = jackson.readTree(json);
        boolean isLast = root.path("is_last").asBoolean(false);
        int updatesReceived = root.path("updates_received").asInt(0);
        int expectedClients = root.path("expected_clients").asInt(1);

        JsonNode timings = root.path("timings");
        SubmitResponse.SubmitTimings t = new SubmitResponse.SubmitTimings(
            Duration.ofMillis(timings.path("sig_model_verify_ms").asLong(0)),
            Duration.ofMillis(timings.path("pbs_verify_ms").asLong(0)),
            Duration.ofMillis(timings.path("state_write_ms").asLong(0)),
            Duration.ofMillis(timings.path("total_ms").asLong(0))
        );
        // El chaincode no conoce los reintentos; BlockchainGateway fija attempts.
        return new SubmitResponse(isLast, updatesReceived, expectedClients, t, 1);
    }

    public AggregationResult parseAggregateResponse(String json, GlobalModel newGlobalModel)
            throws IOException {
        JsonNode root = jackson.readTree(json);
        String status = root.path("status").asText("");
        if ("already_aggregated".equals(status)) {
            long round = root.path("round").asLong(0);
            throw new org.fl.client.exception.AlreadyAggregatedException(round);
        }
        int updatesUsed = root.path("updatesUsed").asInt();
        String tallyHex = root.path("tallyHash").asText();
        TallyHash tally = TallyHash.of(tallyHex);

        JsonNode timings = root.path("timings");
        AggregationResult.ChaincodeTimings chaincodeTimings = new AggregationResult.ChaincodeTimings(
            Duration.ofMillis(timings.path("range_query_ms").asLong(0)),
            Duration.ofMillis(timings.path("aggregation_ms").asLong(0)),
            Duration.ofMillis(timings.path("tally_ms").asLong(0)),
            Duration.ofMillis(timings.path("total_ms").asLong(0))
        );
        return new AggregationResult(newGlobalModel, tally, updatesUsed, chaincodeTimings);
    }

    /** Conserva el orden publicado, que ya es lexicográfico por hex. */
    public List<String> parseUpdateHashesResponse(String json) throws IOException {
        JsonNode root = jackson.readTree(json);
        JsonNode arr = root.path("hashes");
        List<String> hashes = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode el : arr) {
                hashes.add(el.asText());
            }
        }
        return hashes;
    }

    public RoundStatus parseRoundStatusResponse(String json) throws IOException {
        JsonNode root = jackson.readTree(json);
        return new RoundStatus(
            RoundId.of(root.path("active_round").asLong()),
            root.path("total_clients").asInt(1),
            root.path("strategy").asText("fedavg")
        );
    }

    public LeaderInfo parseLeaderInfoResponse(String json) throws IOException {
        JsonNode root = jackson.readTree(json);
        boolean aggregated = root.path("model_aggregated").asBoolean(false);
        int count = root.path("updates_count").asInt(0);
        int expected = root.path("expected_clients").asInt(1);

        JsonNode arr = root.path("anon_cids");
        List<String> hexes = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode el : arr) {
                hexes.add(el.asText());
            }
        }
        return new LeaderInfo(aggregated, count, expected, hexes);
    }
}
