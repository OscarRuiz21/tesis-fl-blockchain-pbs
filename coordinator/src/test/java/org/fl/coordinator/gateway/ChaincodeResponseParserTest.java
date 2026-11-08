package org.fl.coordinator.gateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ChaincodeResponseParserTest {

    private final ChaincodeResponseParser parser = new ChaincodeResponseParser();

    @Test
    void parsesRoundStatusJson() throws IOException {
        String json = "{\"active_round\":3,\"total_clients\":5,\"strategy\":\"fedmedian\"}";

        ChaincodeResponseParser.RoundStatusJson status = parser.parseRoundStatusResponse(json);

        assertThat(status.activeRound().value()).isEqualTo(3L);
        assertThat(status.totalClients()).isEqualTo(5);
        assertThat(status.strategyName()).isEqualTo("fedmedian");
    }

    @Test
    void parsesMinimalStatusWithRound1() throws IOException {
        String json = "{\"active_round\":1,\"total_clients\":2,\"strategy\":\"fedavg\"}";

        ChaincodeResponseParser.RoundStatusJson status = parser.parseRoundStatusResponse(json);

        assertThat(status.activeRound().value()).isEqualTo(1L);
        assertThat(status.totalClients()).isEqualTo(2);
        assertThat(status.strategyName()).isEqualTo("fedavg");
    }
}
