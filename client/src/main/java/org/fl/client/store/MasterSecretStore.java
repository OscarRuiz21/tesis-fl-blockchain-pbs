package org.fl.client.store;

import org.fl.client.model.MasterSecret;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Set;

/**
 * {@link MasterSecret} en disco: se carga si existe y, si no, se genera y se
 * escribe con permisos 0600. Sin persistencia, un cliente reiniciado a mitad
 * del experimento derivaría un {@code anon_sk} que ya no corresponde al
 * nulificador registrado. Se guardan los 32 bytes crudos; PEM no aporta nada
 * para un secreto opaco.
 */
public final class MasterSecretStore {

    private static final Set<PosixFilePermission> PERMS_0600 =
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private MasterSecretStore() {}

    public static MasterSecret loadOrGenerate(Path path, SecureRandom random) throws IOException {
        if (Files.exists(path)) {
            return load(path);
        }
        return generateAndWrite(path, random);
    }

    static MasterSecret load(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        if (raw.length != MasterSecret.LENGTH) {
            throw new IOException(
                "Master secret file " + path + " has " + raw.length
                + " bytes (expected " + MasterSecret.LENGTH + ")");
        }
        return MasterSecret.fromBytes(raw);
    }

    static MasterSecret generateAndWrite(Path path, SecureRandom random) throws IOException {
        MasterSecret secret = MasterSecret.generate(random);
        write(path, secret);
        return secret;
    }

    static void write(Path path, MasterSecret secret) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.deleteIfExists(path);
        Files.write(path, secret.bytes());
        try {
            Files.setPosixFilePermissions(path, PERMS_0600);
        } catch (UnsupportedOperationException e) {
            // Sistema de archivos sin POSIX (Windows); en el contenedor sí aplica.
        }
    }
}
