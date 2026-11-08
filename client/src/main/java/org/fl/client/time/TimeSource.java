package org.fl.client.time;

/**
 * Reloj monotónico para medir duraciones. No se usa {@code Clock}: el reloj
 * de pared salta con NTP, y las operaciones criptográficas duran menos de un
 * milisegundo. Los tests inyectan un reloj controlado.
 */
@FunctionalInterface
public interface TimeSource {
    /** Solo las diferencias tienen sentido. */
    long nanos();
}
