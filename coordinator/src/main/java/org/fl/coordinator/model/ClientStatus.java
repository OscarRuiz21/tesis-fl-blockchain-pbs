package org.fl.coordinator.model;

/** Estado que reporta un fl-client en {@code GET /lifecycle/status}. */
public enum ClientStatus {
    IDLE,
    RUNNING,
    DONE,
    ERROR
}
