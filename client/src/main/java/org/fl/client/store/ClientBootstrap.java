package org.fl.client.store;

import org.fl.client.model.ClientId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link ClientId} de la réplica, asignado por el coordinator en
 * {@code POST /lifecycle/start}.
 *
 * <p>No se deriva del HOSTNAME: con {@code --scale}, el HOSTNAME interno es el
 * id corto del contenedor (p. ej. {@code cfd33848934b}) y la réplica no puede
 * saber qué índice le toca. Este canal de control no forma parte del
 * protocolo; la elegibilidad la valida el AS por mTLS.</p>
 */
@Component
public class ClientBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ClientBootstrap.class);

    private final AtomicReference<ClientId> clientId = new AtomicReference<>();

    /** Repetir el mismo valor no hace nada; uno distinto es un error (dos coordinators). */
    public void assign(ClientId id) {
        ClientId prev = clientId.compareAndExchange(null, id);
        if (prev == null) {
            log.info("ClientId assigned by orchestrator: {}", id.value());
            return;
        }
        if (!prev.equals(id)) {
            throw new IllegalStateException(
                "ClientId already assigned to '" + prev.value()
                + "', cannot reassign to '" + id.value() + "'");
        }
    }

    public ClientId clientId() {
        ClientId v = clientId.get();
        if (v == null) {
            throw new IllegalStateException(
                "ClientId not yet assigned by orchestrator (call /lifecycle/start first)");
        }
        return v;
    }

    public String clientIdOrNull() {
        ClientId v = clientId.get();
        return v == null ? null : v.value();
    }
}
