package org.fl.client.service;

import org.fl.client.model.ClientState;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** Estado compartido entre el hilo del experimento y los endpoints HTTP. */
@Component
public class ClientStateHolder {

    private final AtomicReference<ClientState> state = new AtomicReference<>(ClientState.IDLE);

    public ClientState get() {
        return state.get();
    }

    public void set(ClientState newState) {
        state.set(newState);
    }

    public boolean compareAndSet(ClientState expected, ClientState newState) {
        return state.compareAndSet(expected, newState);
    }
}
