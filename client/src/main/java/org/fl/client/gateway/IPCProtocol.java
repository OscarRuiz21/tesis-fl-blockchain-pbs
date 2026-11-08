package org.fl.client.gateway;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Intercambio de pesos con Python en float32 big-endian. Debe coincidir byte a
 * byte con {@code python/training_core/ipc.py}; de eso depende que el hash de
 * la actualización sea el mismo en el cliente y en el ledger.
 */
@Component
public class IPCProtocol {

    public void writeBinary(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
    }

    public byte[] readBinary(Path path) throws IOException {
        return Files.readAllBytes(path);
    }
}
