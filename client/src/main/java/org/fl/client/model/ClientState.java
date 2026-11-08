package org.fl.client.model;

/** Ciclo de vida del cliente; el coordinator detecta ERROR por sondeo. */
public enum ClientState {
    IDLE, RUNNING, DONE, ERROR
}
