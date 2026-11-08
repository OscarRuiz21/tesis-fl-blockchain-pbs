package org.fl.flprotocol.storage;

import org.fl.flprotocol.serialization.ModelSerializer;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Claves del World State y las operaciones de lectura, validación y escritura
 * sobre ellas.
 *
 * <p>Claves estáticas: {@code as_public_key} (X_AS en Base64),
 * {@code round_status}, {@code total_clients} (K), {@code aggregation_strategy},
 * {@code skip_pbs} y {@code num_params}.</p>
 *
 * <p>Claves por ronda:</p>
 * <ul>
 *   <li>{@code model_round_<r>}: modelo global en Base64.</li>
 *   <li>{@code model_hash_<r>}: {@code H(w_r)} en hex, SHA-256 sobre los bytes
 *       float32 big-endian; el cliente lo usa para detectar un modelo
 *       manipulado.</li>
 *   <li>{@code tally_<r>}: Tally Hash de la ronda.</li>
 *   <li>{@code update_hashes_<r>}: {@code {"hashes":[...]}} con los hashes
 *       individuales ordenados, para {@code queryUpdateHashes}.</li>
 * </ul>
 *
 * <p>Claves por (ronda, {@code anon_cid}), donde el sufijo es el hex en
 * minúsculas del punto sin comprimir (130 caracteres en P-256):</p>
 * <ul>
 *   <li>{@code update_<r>_<anonCidHex>}: actualización en Base64.</li>
 *   <li>{@code nullifier_<r>_<anonCidHex>}: su presencia marca el nulificador
 *       como consumido; el valor guarda el {@code anon_cid} en Base64.</li>
 * </ul>
 *
 * <p>Los updates y el modelo anterior se borran al agregar. Los nulificadores
 * se conservan durante todo el experimento como prueba de uso único.</p>
 */
public final class LedgerKeys {

    static final String KEY_AS_PUBKEY = "as_public_key";
    static final String KEY_ROUND_STATUS = "round_status";
    static final String KEY_TOTAL_CLIENTS = "total_clients";
    static final String KEY_STRATEGY = "aggregation_strategy";
    static final String KEY_SKIP_PBS = "skip_pbs";
    static final String KEY_NUM_PARAMS = "num_params";

    static String modelKey(long round) {
        return "model_round_" + round;
    }

    static String modelHashKey(long round) {
        return "model_hash_" + round;
    }

    static String tallyKey(long round) {
        return "tally_" + round;
    }

    static String updateHashesKey(long round) {
        return "update_hashes_" + round;
    }

    static String updateKey(long round, String anonCidHex) {
        return "update_" + round + "_" + anonCidHex;
    }

    static String nullifierKey(long round, String anonCidHex) {
        return "nullifier_" + round + "_" + anonCidHex;
    }

    // Cota superior de los range reads: '~' (0x7E) es mayor que cualquier dígito hex.
    private static String startKey(String prefix, long round) {
        return prefix + "_" + round + "_";
    }
    private static String endKey(String prefix, long round) {
        return prefix + "_" + round + "_~";
    }

    /**
     * Deja el protocolo en la ronda 1. Puede invocarse de nuevo para reiniciar
     * el experimento sin bajar la red: {@link #cleanupAll} borra antes el estado
     * de la corrida anterior, en particular los nulificadores, que
     * {@link #advance} nunca borra y crecerían sin límite entre corridas.
     */
    public void initialize(
        ChaincodeStub stub,
        String asPublicKeyB64,
        String initialModelB64,
        int totalClients,
        String aggregationStrategy,
        boolean skipPbs,
        int numParams,
        String initialModelHashHex
    ) {
        cleanupAll(stub);
        stub.putStringState(KEY_AS_PUBKEY, asPublicKeyB64);
        stub.putStringState(modelKey(1), initialModelB64);
        // Se publica desde la ronda 1 para que queryGlobalModel(1) ya devuelva
        // un hash auditable.
        stub.putStringState(modelHashKey(1), initialModelHashHex);
        stub.putStringState(KEY_ROUND_STATUS, "1");
        stub.putStringState(KEY_TOTAL_CLIENTS, String.valueOf(totalClients));
        stub.putStringState(KEY_STRATEGY, aggregationStrategy);
        stub.putStringState(KEY_SKIP_PBS, String.valueOf(skipPbs));
        stub.putStringState(KEY_NUM_PARAMS, String.valueOf(numParams));
    }

    /**
     * Borra las claves dinámicas de corridas previas; las estáticas no hacen
     * falta porque {@link #initialize} las sobrescribe. Borrar los
     * nulificadores es válido porque empieza un experimento nuevo y los de la
     * corrida anterior no cuentan para la resistencia a Sybil de esta.
     * Package-private para que los tests lo invoquen directamente.
     */
    void cleanupAll(ChaincodeStub stub) {
        // "update_" ya cubre "update_hashes_"; la pasada explícita evita
        // depender de que ambos prefijos sigan coincidiendo.
        deleteByPrefix(stub, "model_round_");
        deleteByPrefix(stub, "model_hash_");
        deleteByPrefix(stub, "tally_");
        deleteByPrefix(stub, "update_hashes_");
        deleteByPrefix(stub, "update_");
        deleteByPrefix(stub, "nullifier_");
    }

    private static void deleteByPrefix(ChaincodeStub stub, String prefix) {
        for (KeyValue kv : stub.getStateByRange(prefix, prefix + "~")) {
            stub.delState(kv.getKey());
        }
    }

    public void validateRoundActive(ChaincodeStub stub, long requested) {
        long active = readActiveRound(stub);
        if (requested != active) {
            throw ErrorCodes.roundNotActive(requested, active);
        }
    }

    /** Comprueba {@code anon_cid ∉ N_r}; si no, {@code DUPLICATE_NULLIFIER}. */
    public void validateNullifierUnique(ChaincodeStub stub, long round, String anonCidHex) {
        String existing = stub.getStringState(nullifierKey(round, anonCidHex));
        if (existing != null && !existing.isEmpty()) {
            throw ErrorCodes.duplicateNullifier(anonCidHex);
        }
    }

    /** Exige {@code bytesLength == numParams * 4} (float32). */
    public void validateUpdateShape(int bytesLength, int numParams) {
        int got = bytesLength / 4;
        if (bytesLength % 4 != 0 || got != numParams) {
            throw ErrorCodes.invalidNumParamsShape(got, numParams);
        }
    }

    public long readActiveRound(ChaincodeStub stub) {
        return Long.parseLong(stub.getStringState(KEY_ROUND_STATUS));
    }

    public int readTotalClients(ChaincodeStub stub) {
        return Integer.parseInt(stub.getStringState(KEY_TOTAL_CLIENTS));
    }

    public String readStrategy(ChaincodeStub stub) {
        return stub.getStringState(KEY_STRATEGY);
    }

    public String readASPublicKey(ChaincodeStub stub) {
        return stub.getStringState(KEY_AS_PUBKEY);
    }

    public boolean readSkipPbs(ChaincodeStub stub) {
        String raw = stub.getStringState(KEY_SKIP_PBS);
        return "true".equalsIgnoreCase(raw);
    }

    /** Devuelve 0 si la clave no existe; el coordinator siempre la define en {@code initProtocol}. */
    public int readNumParams(ChaincodeStub stub) {
        String raw = stub.getStringState(KEY_NUM_PARAMS);
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(raw);
    }

    public String readModel(ChaincodeStub stub, long round) {
        String b64 = stub.getStringState(modelKey(round));
        if (b64 == null || b64.isEmpty()) {
            throw ErrorCodes.noModelForRound(round);
        }
        return b64;
    }

    /** Como {@link #readModel}, pero devuelve {@code null}; sirve a la idempotencia de {@code aggregateRound}. */
    public String tryReadModelOptional(ChaincodeStub stub, long round) {
        String b64 = stub.getStringState(modelKey(round));
        return (b64 == null || b64.isEmpty()) ? null : b64;
    }

    /**
     * {@code anonCidHex} con nulificador registrado en la ronda, en orden
     * lexicográfico; el primero es el líder. Solo se invoca desde consultas
     * EVALUATE, así que el range read no entra a ningún read-set validado.
     */
    public List<String> readAllAnonCidHexesSorted(ChaincodeStub stub, long round) {
        String prefix = "nullifier_" + round + "_";
        List<String> hexes = new ArrayList<>();
        for (KeyValue kv : stub.getStateByRange(prefix, prefix + "~")) {
            hexes.add(kv.getKey().substring(prefix.length()));
        }
        Collections.sort(hexes);
        return hexes;
    }

    public String readTally(ChaincodeStub stub, long round) {
        String hex = stub.getStringState(tallyKey(round));
        return hex == null ? "" : hex;
    }

    /** {@code H(w_r)} en hex, o cadena vacía si no existe. */
    public String readModelHash(ChaincodeStub stub, long round) {
        String hex = stub.getStringState(modelHashKey(round));
        return hex == null ? "" : hex;
    }

    public List<String> readUpdateHashes(ChaincodeStub stub, long round) {
        String json = stub.getStringState(updateHashesKey(round));
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        return parseHashesJson(json);
    }

    /** JSON crudo de {@code update_hashes_<r>}, o cadena vacía. */
    public String readUpdateHashesJson(ChaincodeStub stub, long round) {
        String json = stub.getStringState(updateHashesKey(round));
        return json == null ? "" : json;
    }

    private static List<String> parseHashesJson(String json) {
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            return List.of();
        }
        String body = json.substring(start + 1, end).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String item : body.split(",")) {
            String trimmed = item.trim();
            if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                out.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Updates de la ronda en el orden lexicográfico de sus claves, que es el
     * mismo en todos los peers y hace determinista la agregación.
     */
    public List<byte[]> readAllUpdates(ChaincodeStub stub, long round) {
        List<byte[]> result = new ArrayList<>();
        for (KeyValue kv : stub.getStateByRange(startKey("update", round), endKey("update", round))) {
            result.add(ModelSerializer.fromBase64(kv.getStringValue()));
        }
        return result;
    }

    /**
     * Cuenta los updates sin decodificarlos. {@code submitUpdate} no lo usa:
     * ese range read entraría al read-set validado por MVCC.
     */
    public int countUpdates(ChaincodeStub stub, long round) {
        int count = 0;
        for (KeyValue ignored : stub.getStateByRange(startKey("update", round), endKey("update", round))) {
            count++;
        }
        return count;
    }

    /** Pares ({@code anon_cid} en Base64, hash del update); el primero sale de {@code nullifier_<r>_<hex>}. */
    public List<UpdateRecord> readAllUpdateRecords(ChaincodeStub stub, long round) {
        List<UpdateRecord> result = new ArrayList<>();
        for (KeyValue kv : stub.getStateByRange(startKey("update", round), endKey("update", round))) {
            String anonCidHex = kv.getKey().substring(("update_" + round + "_").length());
            String anonCidB64 = stub.getStringState(nullifierKey(round, anonCidHex));
            byte[] updateBytes = ModelSerializer.fromBase64(kv.getStringValue());
            String updateHashHex = org.fl.flprotocol.crypto.ProtocolHash.sha256Hex(updateBytes);
            result.add(new UpdateRecord(anonCidB64, updateHashHex));
        }
        return result;
    }

    /** Escribe el update y consume el nulificador en la misma transacción. */
    public void storeUpdate(
        ChaincodeStub stub,
        long round,
        String anonCidHex,
        String updateB64,
        String anonCidB64
    ) {
        stub.putStringState(updateKey(round, anonCidHex), updateB64);
        stub.putStringState(nullifierKey(round, anonCidHex), anonCidB64);
    }

    /**
     * Publica {@code w_{r+1}}, su hash, el Tally Hash y la lista ordenada de
     * hashes individuales, y borra los updates y el modelo de la ronda
     * anterior. Los nulificadores y los hashes individuales se conservan para
     * la auditoría.
     */
    public void advance(
        ChaincodeStub stub,
        long prevRound,
        String newModelB64,
        String newModelHashHex,
        String tallyHashHex,
        List<String> sortedHashesHex
    ) {
        long nextRound = prevRound + 1;

        stub.putStringState(modelKey(nextRound), newModelB64);
        stub.putStringState(modelHashKey(nextRound), newModelHashHex);
        stub.putStringState(tallyKey(prevRound), tallyHashHex);
        stub.putStringState(updateHashesKey(prevRound), buildHashesJson(sortedHashesHex));
        stub.putStringState(KEY_ROUND_STATUS, String.valueOf(nextRound));

        // Se filtra update_hashes_<r> por si cae en el rango de update_<r>_*.
        String hashesKey = updateHashesKey(prevRound);
        for (KeyValue kv : stub.getStateByRange(startKey("update", prevRound), endKey("update", prevRound))) {
            if (!kv.getKey().equals(hashesKey)) {
                stub.delState(kv.getKey());
            }
        }
        stub.delState(modelKey(prevRound));
        // Sin el modelo, su hash ya no sirve para auditar.
        stub.delState(modelHashKey(prevRound));
    }

    /** Sin escapado: las entradas son siempre {@code [a-f0-9]{64}}. */
    private static String buildHashesJson(List<String> sortedHashesHex) {
        StringBuilder sb = new StringBuilder("{\"hashes\":[");
        for (int i = 0; i < sortedHashesHex.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(sortedHashesHex.get(i)).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    public List<String> readAllUpdatesB64(ChaincodeStub stub, long round) {
        List<String> result = new ArrayList<>();
        for (KeyValue kv : stub.getStateByRange(startKey("update", round), endKey("update", round))) {
            result.add(kv.getStringValue());
        }
        return result;
    }

    public record UpdateRecord(String anonCidB64, String updateHashHex) {}
}
