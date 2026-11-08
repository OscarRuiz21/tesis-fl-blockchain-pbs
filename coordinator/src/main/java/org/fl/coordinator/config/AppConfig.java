package org.fl.coordinator.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.fl.coordinator.crypto.PBSDomainParams;
import org.fl.coordinator.gateway.FabricGatewayFactory;
import org.fl.coordinator.security.MtlsContextLoader;
import org.hyperledger.fabric.client.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;

/**
 * Beans del coordinator. Hay dos {@link RestClient}: {@code asRestClient}, con
 * mTLS y CN=coordinator, y {@code clientControlRestClient}, HTTP plano hacia
 * los fl-clients; los gateways los piden por nombre. La conexión a Fabric solo
 * se crea con {@code fl.adapter.blockchain=fabric}.
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public PBSDomainParams pbsDomainParams() {
        return PBSDomainParams.p256();
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // Si faltan los certificados, el arranque falla de inmediato y no a mitad del experimento.
    @Bean(name = "asRestClient")
    public RestClient asRestClient(
            @Value("${fl.auth-server-url}") String asUrl,
            @Value("${fl.coordinator-cert-path}") String certPath,
            @Value("${fl.coordinator-key-path}") String keyPath,
            @Value("${fl.coordinator-ca-path}") String caPath) {
        try {
            SSLContext ssl = MtlsContextLoader.buildMtlsContext(
                Path.of(certPath), Path.of(keyPath), Path.of(caPath));

            CloseableHttpClient http = HttpClients.custom()
                .setConnectionManager(
                    PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(new SSLConnectionSocketFactory(ssl))
                        .build())
                .build();
            return RestClient.builder()
                .baseUrl(asUrl)
                .requestFactory(new HttpComponentsClientHttpRequestFactory(http))
                .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to build mTLS RestClient for coordinator -> AS. "
                + "Check fl.coordinator-{cert,key,ca}-path properties.", e);
        }
    }

    // Sin baseUrl: cada llamada arma su URL con fl.client-url-template.
    @Bean(name = "clientControlRestClient")
    public RestClient clientControlRestClient() {
        return RestClient.builder().build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "fl.adapter.blockchain", havingValue = "fabric", matchIfMissing = true)
    public FabricGatewayFactory.GatewayHandle fabricGatewayHandle(
            @Value("${fl.fabric.channel}") String channel,
            @Value("${fl.fabric.chaincode}") String chaincode,
            @Value("${fl.fabric.msp-id}") String mspId,
            @Value("${fl.fabric.peer-endpoint}") String peerEndpoint,
            @Value("${fl.fabric.peer-override-authority}") String peerOverrideAuthority,
            @Value("${fl.fabric.crypto-path}") String cryptoPath,
            @Value("${fl.fabric.user:Admin}") String userName,
            @Value("${fl.fabric.org-domain:org2.fl.network}") String orgDomain) throws IOException {

        Path basePath = Path.of(cryptoPath);
        Path userMsp = basePath
            .resolve("peerOrganizations").resolve(orgDomain)
            .resolve("users").resolve(userName + "@" + orgDomain)
            .resolve("msp");
        Path peerTls = basePath
            .resolve("peerOrganizations").resolve(orgDomain)
            .resolve("peers").resolve("peer0." + orgDomain)
            .resolve("tls/ca.crt");

        FabricGatewayFactory.FabricConnectionParams params =
            new FabricGatewayFactory.FabricConnectionParams(
                channel, chaincode, mspId,
                peerEndpoint, peerOverrideAuthority,
                userMsp.resolve("signcerts"), userMsp.resolve("keystore"),
                peerTls);
        log.info("Connecting to Fabric peer={} channel={} chaincode={}",
            peerEndpoint, channel, chaincode);
        return FabricGatewayFactory.connect(params);
    }

    @Bean
    @ConditionalOnProperty(name = "fl.adapter.blockchain", havingValue = "fabric", matchIfMissing = true)
    public Contract fabricContract(FabricGatewayFactory.GatewayHandle handle) {
        return handle.contract();
    }
}
