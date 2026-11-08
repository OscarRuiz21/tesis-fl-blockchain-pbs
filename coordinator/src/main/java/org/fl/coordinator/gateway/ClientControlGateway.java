package org.fl.coordinator.gateway;

import org.fl.coordinator.exception.ClientControlException;
import org.fl.coordinator.gateway.ClientControlDtos.StartExperimentRequest;
import org.fl.coordinator.gateway.ClientControlDtos.StatusResponse;
import org.fl.coordinator.model.ClientId;
import org.fl.coordinator.model.ClientStatus;
import org.fl.coordinator.model.ExperimentSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Control de los K fl-clients por HTTP plano: {@code POST /lifecycle/start} y
 * {@code GET /lifecycle/status}. La URL sale de {@code fl.client-url-template}
 * con el índice del {@link ClientId}.
 */
@Component
public class ClientControlGateway {

    private static final Logger log = LoggerFactory.getLogger(ClientControlGateway.class);

    private final RestClient http;
    private final String clientUrlTemplate;

    public ClientControlGateway(
            @Qualifier("clientControlRestClient") RestClient http,
            @Value("${fl.client-url-template}") String clientUrlTemplate) {
        this.http = http;
        this.clientUrlTemplate = clientUrlTemplate;
    }

    /** El cliente corre todas las rondas por su cuenta a partir de aquí. */
    public void startExperiment(ClientId clientId, ExperimentSpec spec) {
        String url = clientUrl(clientId) + "/lifecycle/start";
        StartExperimentRequest body = new StartExperimentRequest(
            clientId.value(),
            spec.label(),
            spec.totalRounds(),
            spec.modelName(),
            spec.numParams(),
            datasetPathFor(clientId, spec),
            spec.aggregationStrategy(),
            spec.anonymityMode(),
            spec.hyperparams());
        try {
            http.post().uri(url).body(body).retrieve().toBodilessEntity();
            log.debug("Started client {} at {}", clientId.value(), url);
        } catch (RestClientException e) {
            log.error("Failed to start client {} at {}", clientId.value(), url, e);
            throw new ClientControlException(clientId.value(),
                "POST /lifecycle/start failed: " + e.getMessage(), e);
        }
    }

    /** Un error de conexión se reporta como IDLE: el cliente puede seguir arrancando. */
    public ClientStatus queryStatus(ClientId clientId) {
        String url = clientUrl(clientId) + "/lifecycle/status";
        try {
            StatusResponse resp = http.get().uri(url).retrieve().body(StatusResponse.class);
            if (resp == null || resp.state() == null) {
                throw new ClientControlException(clientId.value(),
                    "empty status response");
            }
            return ClientStatus.valueOf(resp.state());
        } catch (RestClientException e) {
            log.debug("queryStatus failed for {} (likely not ready yet): {}",
                clientId.value(), e.getMessage());
            return ClientStatus.IDLE;
        }
    }

    private String clientUrl(ClientId clientId) {
        int idx = parseIndex(clientId);
        return String.format(clientUrlTemplate, idx);
    }

    private static String datasetPathFor(ClientId clientId, ExperimentSpec spec) {
        int idx = parseIndex(clientId);
        return spec.datasetBasePath().toString() + "/client-" + idx + ".csv";
    }

    private static int parseIndex(ClientId clientId) {
        String v = clientId.value();
        int dash = v.lastIndexOf('-');
        if (dash < 0) {
            throw new ClientControlException(v, "cannot parse index from clientId");
        }
        try {
            return Integer.parseInt(v.substring(dash + 1));
        } catch (NumberFormatException e) {
            throw new ClientControlException(v, "invalid index in clientId");
        }
    }
}
