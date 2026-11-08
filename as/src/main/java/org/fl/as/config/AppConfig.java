package org.fl.as.config;

import org.fl.as.crypto.PBSDomainParams;
import org.fl.as.model.ASKeyPair;
import org.fl.as.store.PemKeyPairStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

/**
 * Dependencias compartidas del AS: parámetros de P-256, CSPRNG para los
 * nonces y x_AS, par de llaves persistente, reloj (sustituible en tests) y
 * TTL de sesiones.
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

    /**
     * El PEM en {@code auth.keypair-path} sobrevive reinicios, de modo que las
     * credenciales ya emitidas no se invalidan al reiniciar el AS. Para rotar
     * la llave entre experimentos se borra el archivo.
     */
    @Bean
    public ASKeyPair asKeyPair(
            PBSDomainParams params,
            SecureRandom random,
            @Value("${auth.keypair-path}") String keypairPath) throws IOException {
        Path path = Paths.get(keypairPath);
        ASKeyPair pair = PemKeyPairStore.loadOrGenerate(path, params, random);
        log.info("AS keypair loaded/generated at {}", path);
        return pair;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Duration sessionTtl(@Value("${auth.session-ttl-minutes}") long minutes) {
        return Duration.ofMinutes(minutes);
    }
}
