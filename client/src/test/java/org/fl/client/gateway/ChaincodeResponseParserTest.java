package org.fl.client.gateway;

import org.fl.client.model.AggregationResult;
import org.fl.client.model.GlobalModel;
import org.fl.client.model.RoundId;
import org.fl.client.model.SubmitResponse;
import org.fl.client.model.TallyHash;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ChaincodeResponseParserTest {

    private final ChaincodeResponseParser parser = new ChaincodeResponseParser();

    @Test
    void parsesSubmitResponseReturnsIsLastWhenKthClient() throws IOException {
        String json = """
            {"status":"ok","updates_received":3,"expected_clients":3,"is_last":true,
             "timings":{"sig_model_verify_ms":1,"pbs_verify_ms":3,
                        "state_write_ms":7,"total_ms":12}}
            """;

        SubmitResponse r = parser.parseSubmitResponse(json);

        assertThat(r.isLast()).isTrue();
        assertThat(r.updatesReceived()).isEqualTo(3);
        assertThat(r.expectedClients()).isEqualTo(3);
        assertThat(r.timings().sigModelVerify()).isEqualTo(Duration.ofMillis(1));
        assertThat(r.timings().pbsVerify()).isEqualTo(Duration.ofMillis(3));
        assertThat(r.timings().stateWrite()).isEqualTo(Duration.ofMillis(7));
        assertThat(r.timings().total()).isEqualTo(Duration.ofMillis(12));
        // El parser deja attempts=1; el gateway fija el valor real.
        assertThat(r.attempts()).isEqualTo(1);
    }

    @Test
    void parsesSubmitResponseReturnsIsLastFalseWhenNotKth() throws IOException {
        String json = """
            {"status":"ok","updates_received":2,"expected_clients":5,"is_last":false,
             "timings":{"sig_model_verify_ms":1,"pbs_verify_ms":2,
                        "state_write_ms":5,"total_ms":10}}
            """;

        SubmitResponse r = parser.parseSubmitResponse(json);

        assertThat(r.isLast()).isFalse();
        assertThat(r.updatesReceived()).isEqualTo(2);
        assertThat(r.expectedClients()).isEqualTo(5);
    }

    @Test
    void parsesAggregateResponseWithTimings() throws IOException {
        String tallyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String json = """
            {"status":"aggregated","round":1,"updatesUsed":3,"strategy":"fedavg",
             "tallyHash":"%s","newRound":2,
             "timings":{"range_query_ms":15,"aggregation_ms":45,"tally_ms":1,
                        "state_write_ms":8,"total_ms":70}}
            """.formatted(tallyHex);

        // GlobalModel exige una longitud múltiplo de 4 bytes.
        byte[] modelBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.1f).putFloat(0.2f).array();
        GlobalModel newModel = GlobalModel.of(modelBytes, RoundId.of(2), TallyHash.of(tallyHex));

        AggregationResult result = parser.parseAggregateResponse(json, newModel);

        assertThat(result.updatesUsed()).isEqualTo(3);
        assertThat(result.tally().hex()).isEqualTo(tallyHex);
        assertThat(result.timings().rangeQuery()).isEqualTo(Duration.ofMillis(15));
        assertThat(result.timings().total()).isEqualTo(Duration.ofMillis(70));
    }

    @Test
    void parsesRoundStatus() throws IOException {
        String json = "{\"active_round\":3,\"total_clients\":5,\"strategy\":\"fedmedian\"}";

        var status = parser.parseRoundStatusResponse(json);

        assertThat(status.activeRound().value()).isEqualTo(3L);
        assertThat(status.totalClients()).isEqualTo(5);
        assertThat(status.strategyName()).isEqualTo("fedmedian");
    }
}
