package org.fl.as.crypto;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Paridad byte a byte de los hashes entre Java y Python.
 *
 * <p>{@code serializationHashIdenticalJavaPython} cubre el camino real: Python
 * serializa los {@code Δw} entrenados como float32 big-endian y el cliente Java
 * lee ese archivo; si las convenciones divergieran, el chaincode recibiría
 * bytes distintos de lo entrenado sin que nada fallara.</p>
 *
 * <p>{@code tallyHashIdenticalJavaPython} usa {@code compute_tally_hash.py}
 * como implementación de referencia independiente (en el protocolo el Tally
 * Hash solo lo calcula el chaincode). Detecta errores de codificación, orden o
 * endianness que una prueba Java contra Java no vería; de ese algoritmo
 * dependen P4 y P5.</p>
 *
 * <p>Si no existe {@code python/.venv}, las pruebas se omiten en vez de fallar.</p>
 */
class JavaPythonHashCompatibilityTest {

    private static final String VENV_PYTHON_REL = "python/.venv/bin/python";
    private static final String COMPUTE_HASH_REL = "python/tests/compute_hash.py";
    private static final String COMPUTE_TALLY_REL = "python/tests/compute_tally_hash.py";

    private static Path projectRoot;
    private static Path venvPython;
    private static Path computeHashScript;
    private static Path computeTallyScript;

    @BeforeAll
    static void resolveProjectRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        // Maven puede invocar el test desde as/ o desde la raíz del proyecto.
        if (cwd.getFileName() != null && "as".equals(cwd.getFileName().toString())) {
            projectRoot = cwd.getParent();
        } else {
            projectRoot = cwd;
        }
        venvPython = projectRoot.resolve(VENV_PYTHON_REL);
        computeHashScript = projectRoot.resolve(COMPUTE_HASH_REL);
        computeTallyScript = projectRoot.resolve(COMPUTE_TALLY_REL);
    }

    @Test
    @DisplayName("SHA-256 de bytes float32 big-endian idéntico Java vs Python")
    void serializationHashIdenticalJavaPython() throws Exception {
        skipIfVenvMissing();
        skipIfScriptMissing(computeHashScript);

        float[] weights = randomFloats(128, 42L);
        byte[] javaBytes = serializeFloatsBE(weights);
        String javaHex = sha256Hex(javaBytes);

        Path tmp = Files.createTempFile("h_test_", ".bin");
        try {
            Files.write(tmp, javaBytes);
            String pythonHex = runPython(
                computeHashScript.toString(),
                "--input", tmp.toString());
            assertEquals(javaHex, pythonHex,
                "SHA-256 de bytes serializados debe ser idéntico Java vs Python");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("Tally_Hash idéntico Java (ProtocolHash) vs Python (compute_tally_hash.py)")
    void tallyHashIdenticalJavaPython() throws Exception {
        skipIfVenvMissing();
        skipIfScriptMissing(computeTallyScript);

        List<byte[]> updates = new ArrayList<>();
        long[] seeds = {1L, 2L, 3L, 4L, 5L};
        int[] sizes = {26, 50, 100, 7, 200};
        for (int i = 0; i < seeds.length; i++) {
            updates.add(serializeFloatsBE(randomFloats(sizes[i], seeds[i])));
        }

        String javaHex = ProtocolHash.computeTallyHashHex(updates);

        List<Path> tempFiles = new ArrayList<>();
        try {
            for (int i = 0; i < updates.size(); i++) {
                Path f = Files.createTempFile("u_" + i + "_", ".bin");
                Files.write(f, updates.get(i));
                tempFiles.add(f);
            }

            String[] cmd = new String[2 + tempFiles.size()];
            cmd[0] = venvPython.toString();
            cmd[1] = computeTallyScript.toString();
            for (int i = 0; i < tempFiles.size(); i++) {
                cmd[2 + i] = tempFiles.get(i).toString();
            }
            String pythonHex = runProcess(cmd);

            assertEquals(javaHex, pythonHex,
                "Tally_Hash debe ser idéntico Java vs Python (hashes ordenados por hex)");
        } finally {
            for (Path f : tempFiles) {
                Files.deleteIfExists(f);
            }
        }
    }

    private static byte[] serializeFloatsBE(float[] data) {
        // Misma lógica que ModelSerializer.serializeFloats del cliente.
        ByteBuffer bb = ByteBuffer.allocate(data.length * 4).order(ByteOrder.BIG_ENDIAN);
        for (float v : data) {
            bb.putFloat(v);
        }
        return bb.array();
    }

    private static void skipIfVenvMissing() {
        Assumptions.assumeTrue(
            Files.isExecutable(venvPython),
            "Skipped: " + venvPython + " no existe. Crear con: "
                + "python3 -m venv python/.venv && "
                + "python/.venv/bin/pip install -r python/requirements.txt");
    }

    private static void skipIfScriptMissing(Path script) {
        Assumptions.assumeTrue(
            Files.exists(script),
            "Skipped: utility script no existe en " + script);
    }

    private static float[] randomFloats(int size, long seed) {
        Random rnd = new Random(seed);
        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            out[i] = (rnd.nextFloat() * 2f) - 1f;
        }
        return out;
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String runPython(String... args) throws Exception {
        String[] cmd = new String[args.length + 1];
        cmd[0] = venvPython.toString();
        System.arraycopy(args, 0, cmd, 1, args.length);
        return runProcess(cmd);
    }

    private static String runProcess(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(projectRoot.toFile());
        Process p = pb.start();
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            fail("Python subprocess no terminó en 30s: " + String.join(" ", cmd));
        }
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (p.exitValue() != 0) {
            fail("Python subprocess exit " + p.exitValue() + ": " + stdout);
        }
        return stdout;
    }
}
