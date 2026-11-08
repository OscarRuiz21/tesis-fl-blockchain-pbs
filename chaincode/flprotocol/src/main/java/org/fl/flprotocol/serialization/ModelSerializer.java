package org.fl.flprotocol.serialization;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Base64;

/**
 * Pesos del modelo como float32 IEEE 754 big-endian, el mismo formato que
 * escribe Python con {@code struct.pack('&gt;Nf')}. En el World State se
 * guardan en Base64. Los hashes del protocolo se calculan sobre estos bytes,
 * así que cambiar el formato rompe la paridad con el cliente.
 */
public final class ModelSerializer {

    private ModelSerializer() {}

    public static byte[] serializeFloats(float[] data) {
        ByteBuffer bb = ByteBuffer.allocate(data.length * 4)
                .order(ByteOrder.BIG_ENDIAN);
        for (float v : data) {
            bb.putFloat(v);
        }
        return bb.array();
    }

    public static float[] deserializeFloats(byte[] bytes) {
        FloatBuffer fb = ByteBuffer.wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .asFloatBuffer();
        float[] result = new float[fb.remaining()];
        fb.get(result);
        return result;
    }

    public static String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] fromBase64(String b64) {
        return Base64.getDecoder().decode(b64);
    }

    /**
     * Extrae el campo {@code "model"} del JSON de {@code queryGlobalModel} sin
     * un parser JSON, aprovechando que el contrato genera ese JSON con formato fijo.
     *
     * @throws IllegalArgumentException si falta el campo {@code "model"}
     */
    public static byte[] extractModelBytes(String json) {
        String key = "\"model\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            throw new IllegalArgumentException("No 'model' field in JSON");
        }
        start += key.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            throw new IllegalArgumentException("Malformed JSON: unterminated model value");
        }
        return fromBase64(json.substring(start, end));
    }
}
