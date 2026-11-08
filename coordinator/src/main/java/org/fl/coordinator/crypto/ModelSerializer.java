package org.fl.coordinator.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Base64;

/**
 * Pesos del modelo como float32 big-endian (IEEE 754), el mismo formato que
 * usan el chaincode y Python ({@code struct.pack('>Nf')}). Copia del
 * serializador del chaincode; el coordinator lo usa para el modelo inicial en
 * ceros y para leer el modelo global que evalúa {@code eval.py}.
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

    /** Extrae {@code model} de {@code {"model":"<base64>","tally_hash":"<hex>","round":"<n>"}}. */
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
