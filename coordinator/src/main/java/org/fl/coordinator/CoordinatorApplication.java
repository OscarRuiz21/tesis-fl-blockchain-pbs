package org.fl.coordinator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Coordinator del prototipo de pruebas. Recibe el YAML de un experimento en
 * {@code POST /experiments/run}, inicializa el contrato y el AS, manda el
 * start a los clientes y evalúa el modelo global de cada ronda. No participa
 * en el protocolo: la emisión de credenciales, el envío de actualizaciones y
 * la agregación ocurren entre clientes, AS y contrato. La red, los
 * certificados y las particiones los prepara {@code scripts/run-experiment.sh}.
 */
@SpringBootApplication
public class CoordinatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoordinatorApplication.class, args);
    }
}
