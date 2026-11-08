package org.fl.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Cliente FL. Se escala a K réplicas con docker-compose; cada una recibe su
 * {@code ClientId} del coordinator por {@code POST /lifecycle/start}.
 */
@SpringBootApplication
public class FLClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(FLClientApplication.class, args);
    }
}
