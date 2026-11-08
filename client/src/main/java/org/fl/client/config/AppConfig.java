package org.fl.client.config;

import org.fl.client.crypto.PBSDomainParams;
import org.fl.client.gateway.FabricGatewayFactory;
import org.fl.client.model.MasterSecret;
import org.fl.client.store.MasterSecretStore;
import org.hyperledger.fabric.client.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuración del cliente.
 *
 * <p>El {@link MasterSecret} se persiste en disco: si se regenerara tras una
 * caída a mitad del experimento, el nuevo {@code anon_sk_r} ya no
 * correspondería al {@code anon_cid_r} que el chaincode registró como
 * nulificador y el cliente quedaría fuera de esa ronda.</p>
 *
 * <p>En el perfil {@code test} no se conecta a Fabric, para que las pruebas
 * no necesiten material criptográfico real.</p>
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
    @Profile("!test")
    public MasterSecret masterSecret(
            @Value("${fl.master-secret-path}") String masterSecretPath,
            SecureRandom random) throws IOException {
        Path path = Path.of(masterSecretPath);
        boolean preexisting = java.nio.file.Files.exists(path);
        MasterSecret secret = MasterSecretStore.loadOrGenerate(path, random);
        log.info("Master secret {} at {}",
            preexisting ? "loaded" : "generated and written", path);
        return secret;
    }

    @Bean
    @Profile("test")
    public MasterSecret testMasterSecret(SecureRandom random) {
        return MasterSecret.generate(random);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Hilo daemon para que un experimento en curso no bloquee el apagado ante SIGTERM. */
    @Bean
    public ExecutorService experimentExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fl-client-experiment");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    @Profile("!test")
    public FabricGatewayFactory.GatewayHandle fabricGatewayHandle(
            @Value("${fl.fabric.channel}") String channel,
            @Value("${fl.fabric.chaincode}") String chaincode,
            @Value("${fl.fabric.msp-id}") String mspId,
            @Value("${fl.fabric.peer-endpoint}") String peerEndpoint,
            @Value("${fl.fabric.peer-override-authority}") String peerOverrideAuthority,
            @Value("${fl.fabric.crypto-path}") String cryptoPath,
            @Value("${fl.fabric.user}") String userName) throws IOException {

        // Estructura de fabric-samples: users/<User>@<org>/msp/{signcerts,keystore}
        // y peers/peer0.<org>/tls/ca.crt bajo peerOrganizations/<org>/.
        String orgDomain = "org2.fl.network";
        Path basePath = Path.of(cryptoPath);
        Path userMsp = basePath
            .resolve("peerOrganizations")
            .resolve(orgDomain)
            .resolve("users")
            .resolve(userName + "@" + orgDomain)
            .resolve("msp");
        Path peerTls = basePath
            .resolve("peerOrganizations")
            .resolve(orgDomain)
            .resolve("peers")
            .resolve("peer0." + orgDomain)
            .resolve("tls/ca.crt");

        var params = new FabricGatewayFactory.FabricConnectionParams(
            channel, chaincode, mspId, peerEndpoint, peerOverrideAuthority,
            userMsp.resolve("signcerts"),
            userMsp.resolve("keystore"),
            peerTls);
        return FabricGatewayFactory.connect(params);
    }

    @Bean
    @Profile("!test")
    public Contract fabricContract(FabricGatewayFactory.GatewayHandle handle) {
        return handle.contract();
    }
}
