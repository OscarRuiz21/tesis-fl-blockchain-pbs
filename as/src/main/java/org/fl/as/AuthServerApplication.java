package org.fl.as;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servidor de autenticación (AS) de la Etapa 1 (Cap. 4 §4.2.1): emite
 * credenciales PBS sin conocer el mensaje que firma. El scheduling purga las
 * sesiones de firma expiradas.
 */
@SpringBootApplication
@EnableScheduling
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
