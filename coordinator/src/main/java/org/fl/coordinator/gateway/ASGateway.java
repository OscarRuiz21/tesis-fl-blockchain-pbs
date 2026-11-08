package org.fl.coordinator.gateway;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.coordinator.crypto.CryptoCodec;
import org.fl.coordinator.crypto.PBSDomainParams;
import org.fl.coordinator.exception.AuthenticationFailedException;
import org.fl.coordinator.gateway.ASDtos.AdminInitRequest;
import org.fl.coordinator.gateway.ASDtos.PublicKeyResponse;
import org.fl.coordinator.model.ClientId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Cliente mTLS del AS. Usa dos endpoints: {@code POST /admin/init}, que carga
 * la whitelist y solo acepta CN=coordinator, y {@code GET /blind-sign/public-key},
 * que da la {@code X_AS} que se registra en el contrato.
 */
// Constructor explícito: Lombok no copiaría el @Qualifier al parámetro.
@Component
public class ASGateway {

    private static final Logger log = LoggerFactory.getLogger(ASGateway.class);

    private final RestClient asRestClient;
    private final PBSDomainParams params;

    public ASGateway(
            @Qualifier("asRestClient") RestClient asRestClient,
            PBSDomainParams params) {
        this.asRestClient = asRestClient;
        this.params = params;
    }

    public void initWhitelist(List<ClientId> clients, int totalRounds, String experimentId) {
        try {
            asRestClient.post()
                .uri("/admin/init")
                .body(new AdminInitRequest(
                    clients.stream().map(ClientId::value).toList(),
                    totalRounds,
                    experimentId))
                .retrieve()
                .toBodilessEntity();
            log.info("AS whitelist initialized: experimentId={} clients={} totalRounds={}",
                experimentId, clients.size(), totalRounds);
        } catch (RestClientException e) {
            throw new AuthenticationFailedException("admin/init failed", e);
        }
    }

    public ECPoint fetchServerPublicKey() {
        try {
            PublicKeyResponse resp = asRestClient.get()
                .uri("/blind-sign/public-key")
                .retrieve()
                .body(PublicKeyResponse.class);
            if (resp == null || resp.publicKeyB64() == null) {
                throw new AuthenticationFailedException("AS returned empty pubkey response");
            }
            return CryptoCodec.decodePoint(resp.publicKeyB64(), params.curve());
        } catch (RestClientException e) {
            throw new AuthenticationFailedException("fetchServerPublicKey failed", e);
        }
    }
}
