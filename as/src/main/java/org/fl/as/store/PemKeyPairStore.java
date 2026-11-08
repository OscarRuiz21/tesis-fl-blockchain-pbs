package org.fl.as.store;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.fl.as.crypto.PBSDomainParams;
import org.fl.as.model.ASKeyPair;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Set;

/**
 * Persistencia del par de llaves del AS: si el archivo existe se carga, si no
 * se genera y se escribe con permisos {@code 0600}.
 *
 * <p>El PEM usa una etiqueta propia ({@code "FL AS PRIVATE KEY"}) y guarda solo
 * los 32 bytes big-endian de {@code x_AS}; así no se depende de los parsers
 * PKCS#8 o SEC1, que cambian entre versiones de Bouncy Castle. La llave pública
 * se reconstruye como {@code X_AS = x_AS·G}.</p>
 */
public final class PemKeyPairStore {

    private static final String PEM_LABEL = "FL AS PRIVATE KEY";
    private static final Set<PosixFilePermission> PERMS_0600 =
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private PemKeyPairStore() {}

    public static ASKeyPair loadOrGenerate(
            Path path, PBSDomainParams params, SecureRandom random) throws IOException {
        if (Files.exists(path)) {
            return load(path, params);
        }
        return generateAndWrite(path, params, random);
    }

    static ASKeyPair load(Path path, PBSDomainParams params) throws IOException {
        String pem = Files.readString(path);
        try (PemReader reader = new PemReader(new StringReader(pem))) {
            PemObject obj = reader.readPemObject();
            if (obj == null) {
                throw new IOException("Empty or invalid PEM file: " + path);
            }
            if (!PEM_LABEL.equals(obj.getType())) {
                throw new IOException(
                    "Unexpected PEM label '" + obj.getType() + "' (expected '" + PEM_LABEL + "')");
            }
            BigInteger d = new BigInteger(1, obj.getContent());
            return new ASKeyPair(d, params.G().multiply(d).normalize());
        }
    }

    static ASKeyPair generateAndWrite(
            Path path, PBSDomainParams params, SecureRandom random) throws IOException {
        ASKeyPair pair = ASKeyPair.generate(params, random);
        write(path, pair);
        return pair;
    }

    static void write(Path path, ASKeyPair pair) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        byte[] scalar = toFixed32BE(pair.privateKey());
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(new PemObject(PEM_LABEL, scalar));
        }
        try {
            Files.deleteIfExists(path);
            Files.writeString(path, sw.toString());
            Files.setPosixFilePermissions(path, PERMS_0600);
        } catch (UnsupportedOperationException e) {
            // Sistema de archivos sin POSIX (Windows en desarrollo); en el
            // contenedor Linux sí se aplica el chmod.
            Files.writeString(path, sw.toString());
        }
    }

    /** El escalar puede tener menos de 32 bytes significativos; se rellena para que el formato sea fijo. */
    private static byte[] toFixed32BE(BigInteger scalar) {
        byte[] raw = scalar.toByteArray();
        byte[] out = new byte[32];
        if (raw.length <= 32) {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        } else {
            // Descarta el byte de signo (0x00) que BigInteger agrega para positivos.
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        }
        return out;
    }
}
