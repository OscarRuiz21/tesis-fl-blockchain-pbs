package org.fl.flprotocol;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.flprotocol.aggregator.AggregationEngine;
import org.fl.flprotocol.crypto.CryptoCodec;
import org.fl.flprotocol.crypto.ModelSigner;
import org.fl.flprotocol.crypto.PBSDomainParams;
import org.fl.flprotocol.crypto.ProtocolHash;
import org.fl.flprotocol.serialization.ModelSerializer;
import org.fl.flprotocol.storage.ErrorCodes;
import org.fl.flprotocol.storage.LedgerKeys;
import org.fl.flprotocol.verifier.PBSVerifier;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeStub;

import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Contrato (BSC) del protocolo PBS-FL sobre Hyperledger Fabric 2.5. Solo enruta:
 * la verificación de la credencial está en {@link PBSVerifier}, la agregación en
 * {@link AggregationEngine} y el acceso al World State en {@link LedgerKeys}.
 *
 * <p>{@code submitUpdate} y {@code aggregateRound} devuelven en JSON los tiempos
 * internos que usa el análisis de costo del Cap. 6.</p>
 *
 * <p>La bandera {@code skip_pbs} de {@code initProtocol} desactiva la
 * verificación PBS en las corridas de referencia sin anonimato; la ronda activa,
 * la unicidad del nulificador y la dimensión del update se validan siempre.</p>
 */
@Contract(name = "FLProtocolChaincode")
@Default
public final class FLProtocolChaincode implements ContractInterface {

    private static final PBSDomainParams PARAMS = PBSDomainParams.p256();

    private final PBSVerifier verifier = new PBSVerifier(PARAMS);
    private final AggregationEngine aggregator = new AggregationEngine();
    private final LedgerKeys keys = new LedgerKeys();

    /**
     * Inicializa el protocolo; se invoca una sola vez por experimento.
     *
     * @param numParamsStr dimensión del vector de pesos ({@code model.num_params}
     *                     del YAML del experimento); se valida en cada
     *                     {@code submitUpdate}.
     * @param skipPbsStr   "true" desactiva la verificación PBS (referencia sin
     *                     anonimato).
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void initProtocol(Context ctx,
                             String asPublicKeyB64,
                             String initialModelB64,
                             String totalClientsStr,
                             String aggregationStrategy,
                             String skipPbsStr,
                             String numParamsStr) {
        ChaincodeStub stub = ctx.getStub();

        int totalClients = Integer.parseInt(totalClientsStr);
        if (totalClients < 1) {
            throw ErrorCodes.invalidTotalClients(totalClients);
        }

        int numParams = Integer.parseInt(numParamsStr);
        if (numParams < 1) {
            throw ErrorCodes.invalidNumParams(numParams);
        }

        if (!aggregator.isKnownStrategy(aggregationStrategy)) {
            throw ErrorCodes.unknownStrategy(aggregationStrategy);
        }

        boolean skipPbs = "true".equalsIgnoreCase(skipPbsStr);

        // H(w_1), compromiso de integridad del modelo inicial: SHA-256 sobre los
        // bytes float32 big-endian, la misma representación que recompone el
        // cliente al auditar el modelo global.
        String initialModelHashHex = ProtocolHash.sha256Hex(
            ModelSerializer.fromBase64(initialModelB64));

        keys.initialize(stub, asPublicKeyB64, initialModelB64,
            totalClients, aggregationStrategy, skipPbs, numParams, initialModelHashHex);
    }

    /**
     * Recibe una actualización anónima (Etapa 2, §4.2.2). Las comprobaciones van
     * en este orden:
     * <ol>
     *   <li>Ronda activa ({@code ROUND_NOT_ACTIVE}).</li>
     *   <li>Dimensión del update, {@code length == numParams * 4} bytes
     *       ({@code INVALID_NUM_PARAMS_SHAPE}); es barata y va antes de cualquier
     *       operación sobre la curva.</li>
     *   <li>P6: la firma de autoría {@code σ_model} sobre {@code Δw} verifica
     *       contra {@code anon_cid} ({@code INVALID_SIG_MODEL}); impide sustituir
     *       el update en tránsito.</li>
     *   <li>PBS: la credencial es válida ({@code INVALID_PBS_SIGNATURE}), lo que
     *       prueba la elegibilidad.</li>
     *   <li>Nulificador: el {@code anon_cid}, que hace de nulificador, no se usó
     *       antes en la ronda ({@code DUPLICATE_NULLIFIER}). Va después de la PBS
     *       porque solo una credencial válida lo ancla criptográficamente.</li>
     * </ol>
     *
     * <p>Con {@code skip_pbs == true} se omiten P6 y PBS: la referencia sin
     * anonimato no deriva clave anónima y por lo tanto no firma el update.</p>
     *
     * <p>P6 va antes de la PBS porque ECDSA es más barato que la verificación de
     * la credencial (que además recalcula el Hash-to-Curve); si {@code Δw} se
     * alteró, se rechaza sin gastar ese cómputo. Cualquier fallo aborta la
     * transacción sin escribir.</p>
     *
     * @return JSON con los tiempos de la transacción:
     *         {@code {"status":"ok","updates_received":-1,"expected_clients":K,
     *         "is_last":false,"timings":{...}}}. {@code updates_received} va en
     *         -1 e {@code is_last} en false porque contar los updates metería un
     *         range read al read-set validado por MVCC; el cliente decide la
     *         agregación con {@link #getRoundLeaderInfo}.
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String submitUpdate(Context ctx,
                               String anonCidB64,
                               String rhoB64,
                               String omegaB64,
                               String sigmaB64,
                               String deltaB64,
                               String updateB64,
                               String sigModelB64,
                               String roundIdStr) {
        long t0 = System.nanoTime();
        ChaincodeStub stub = ctx.getStub();
        long roundId = Long.parseLong(roundIdStr);

        keys.validateRoundActive(stub, roundId);

        // Solo se deriva el hex canónico; el nulificador se consume más abajo,
        // después de validar la PBS.
        String anonCidHex = deriveAnonCidHex(anonCidB64);

        byte[] updateBytes = Base64.getDecoder().decode(updateB64);
        keys.validateUpdateShape(updateBytes.length, keys.readNumParams(stub));

        long sigModelMs = 0;
        long pbsMs = 0;

        if (!keys.readSkipPbs(stub)) {
            // P6: σ_model = Sign(anon_sk, Δw).
            long tSigStart = System.nanoTime();
            boolean sigOk = verifySigModel(anonCidB64, updateB64, sigModelB64);
            sigModelMs = (System.nanoTime() - tSigStart) / 1_000_000;
            if (!sigOk) {
                throw ErrorCodes.invalidSigModel();
            }

            long tPbsStart = System.nanoTime();
            String xasB64 = keys.readASPublicKey(stub);
            boolean pbsOk = verifier.verify(
                rhoB64, omegaB64, sigmaB64, deltaB64, anonCidB64, roundId, xasB64);
            pbsMs = (System.nanoTime() - tPbsStart) / 1_000_000;
            if (!pbsOk) {
                throw ErrorCodes.invalidPbsSignature();
            }
        }

        // Se valida también con skip_pbs.
        keys.validateNullifierUnique(stub, roundId, anonCidHex);

        long tStoreStart = System.nanoTime();
        keys.storeUpdate(stub, roundId, anonCidHex, updateB64, anonCidB64);
        long storeMs = (System.nanoTime() - tStoreStart) / 1_000_000;

        // is_last y updates_received van fijos (ver @return); el parser del
        // cliente todavía los espera.
        int expectedClients = keys.readTotalClients(stub);

        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        return String.format(
            "{\"status\":\"ok\",\"updates_received\":-1,"
            + "\"expected_clients\":%d,\"is_last\":false,\"timings\":{"
            + "\"sig_model_verify_ms\":%d,\"pbs_verify_ms\":%d,"
            + "\"state_write_ms\":%d,\"total_ms\":%d}}",
            expectedClients,
            sigModelMs, pbsMs, storeMs, totalMs);
    }

    /**
     * Hex canónico del {@code anon_cid} (punto sin comprimir, 130 caracteres en
     * P-256); es el sufijo de las claves {@code update_<r>_<hex>} y
     * {@code nullifier_<r>_<hex>}.
     */
    private static String deriveAnonCidHex(String anonCidB64) {
        ECPoint point = CryptoCodec.decodePoint(anonCidB64, PARAMS.curve());
        return HexFormat.of().formatHex(point.getEncoded(false));
    }

    /**
     * Verificación de P6. Cualquier excepción (Base64 o punto inválido) se
     * reduce a {@code false} para no filtrar diagnóstico al cliente.
     */
    private static boolean verifySigModel(String anonCidB64, String updateB64, String sigModelB64) {
        try {
            ECPoint anonCid = CryptoCodec.decodePoint(anonCidB64, PARAMS.curve());
            byte[] update = Base64.getDecoder().decode(updateB64);
            byte[] sigDer = Base64.getDecoder().decode(sigModelB64);
            return ModelSigner.verify(anonCid, update, sigDer, PARAMS);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Agrega on-chain las actualizaciones de la ronda (Etapa 3, §4.2.3). La
     * invoca el cliente líder, el de menor {@code anonCidHex} según
     * {@link #getRoundLeaderInfo}. Si {@code model_<r+1>} ya existe porque otro
     * cliente o un failover ya agregó, devuelve {@code already_aggregated} sin
     * escribir y sin repetir el range read ni el Tally Hash. Una invocación con
     * menos de K updates se rechaza.
     *
     * @return JSON con tiempos:
     *         {@code {"status":"aggregated"|"already_aggregated","round":...,"timings":{...}}}.
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String aggregateRound(Context ctx, String roundIdStr) {
        long t0 = System.nanoTime();
        ChaincodeStub stub = ctx.getStub();
        long roundId = Long.parseLong(roundIdStr);

        keys.validateRoundActive(stub, roundId);

        if (keys.tryReadModelOptional(stub, roundId + 1) != null) {
            long totalMs = (System.nanoTime() - t0) / 1_000_000;
            return String.format(
                "{\"status\":\"already_aggregated\",\"round\":%d,"
                + "\"newRound\":%d,\"timings\":{"
                + "\"range_query_ms\":0,\"aggregation_ms\":0,"
                + "\"tally_ms\":0,\"state_write_ms\":0,\"total_ms\":%d}}",
                roundId, roundId + 1, totalMs);
        }

        long tRangeStart = System.nanoTime();
        List<byte[]> rawUpdates = keys.readAllUpdates(stub, roundId);
        long rangeQueryMs = (System.nanoTime() - tRangeStart) / 1_000_000;

        int totalClients = keys.readTotalClients(stub);
        if (rawUpdates.size() < totalClients) {
            throw ErrorCodes.notEnoughUpdates(rawUpdates.size(), totalClients, roundId);
        }

        String strategy = keys.readStrategy(stub);

        List<float[]> floatUpdates = rawUpdates.stream()
            .map(ModelSerializer::deserializeFloats)
            .toList();

        String currentModelB64 = keys.readModel(stub, roundId);
        float[] currentModel = ModelSerializer.deserializeFloats(
            ModelSerializer.fromBase64(currentModelB64));

        long tAggStart = System.nanoTime();
        float[] newModel = aggregator.aggregate(strategy, currentModel, floatUpdates);
        long aggMs = (System.nanoTime() - tAggStart) / 1_000_000;

        // Tally Hash: SHA-256 de cada update, orden lexicográfico por hex,
        // concatenación de los binarios y SHA-256 final. La lista ordenada se
        // guarda en update_hashes_<r> para queryUpdateHashes.
        long tTallyStart = System.nanoTime();
        List<String> sortedHashesHex = ProtocolHash.computeSortedUpdateHashesHex(rawUpdates);
        String tallyHashHex = ProtocolHash.computeTallyHashHexFromSortedHashes(sortedHashesHex);
        long tallyMs = (System.nanoTime() - tTallyStart) / 1_000_000;

        long tWriteStart = System.nanoTime();
        byte[] newModelBytes = ModelSerializer.serializeFloats(newModel);
        String newModelB64 = ModelSerializer.toBase64(newModelBytes);
        // H(w_{r+1}): mismos bytes que recompone el cliente al descargar el
        // modelo, para que detecte manipulaciones antes de usarlo.
        String newModelHashHex = ProtocolHash.sha256Hex(newModelBytes);
        keys.advance(stub, roundId, newModelB64, newModelHashHex, tallyHashHex, sortedHashesHex);
        long writeMs = (System.nanoTime() - tWriteStart) / 1_000_000;

        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        long nextRound = roundId + 1;

        return String.format(
            "{\"status\":\"aggregated\",\"round\":%d,\"updatesUsed\":%d,"
            + "\"strategy\":\"%s\",\"tallyHash\":\"%s\",\"newRound\":%d,"
            + "\"timings\":{\"range_query_ms\":%d,\"aggregation_ms\":%d,"
            + "\"tally_ms\":%d,\"state_write_ms\":%d,\"total_ms\":%d}}",
            roundId, rawUpdates.size(), strategy, tallyHashHex, nextRound,
            rangeQueryMs, aggMs, tallyMs, writeMs, totalMs);
    }

    // Consultas de solo lectura

    /**
     * @return JSON: {@code {"model":"<b64>","model_hash":"<hex>",
     *         "tally_hash":"<hex>","round":"<n>"}}.
     *         {@code model_hash} es {@code H(w_r)}; {@code tally_hash} va vacío
     *         si {@code roundId == 1}.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryGlobalModel(Context ctx, String roundIdStr) {
        ChaincodeStub stub = ctx.getStub();
        long roundId = Long.parseLong(roundIdStr);
        String modelB64 = keys.readModel(stub, roundId);
        String modelHashHex = keys.readModelHash(stub, roundId);
        String tallyHex = "";
        if (roundId > 1) {
            tallyHex = keys.readTally(stub, roundId - 1);
        }
        return String.format(
            "{\"model\":\"%s\",\"model_hash\":\"%s\",\"tally_hash\":\"%s\",\"round\":\"%d\"}",
            modelB64, modelHashHex, tallyHex, roundId);
    }

    /**
     * @return JSON: {@code {"active_round":N,"total_clients":K,"strategy":"<name>"}}.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryRoundStatus(Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        long round = keys.readActiveRound(stub);
        int total = keys.readTotalClients(stub);
        String strategy = keys.readStrategy(stub);
        return String.format(
            "{\"active_round\":%d,\"total_clients\":%d,\"strategy\":\"%s\"}",
            round, total, strategy);
    }

    /**
     * @return JSON: {@code {"updates":["<b64>",...],"count":N}}, en el orden
     *         lexicográfico de las claves del ledger (el Tally Hash no depende de
     *         él porque ordena por el hash de cada update).
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryUpdates(Context ctx, String roundIdStr) {
        ChaincodeStub stub = ctx.getStub();
        long roundId = Long.parseLong(roundIdStr);
        List<String> updates = keys.readAllUpdatesB64(stub, roundId);

        StringBuilder sb = new StringBuilder("{\"updates\":[");
        for (int i = 0; i < updates.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(updates.get(i)).append("\"");
        }
        sb.append("],\"count\":").append(updates.size()).append("}");
        return sb.toString();
    }

    /**
     * Lista ordenada de hashes {@code {h_r^k}} de la ronda, base de la
     * verificabilidad individual (P4) y universal (P5): el cliente busca
     * {@code H(Δw_local)} en la lista y puede recalcular {@code T_r}
     * concatenando los hashes en el orden recibido (32 bytes binarios cada uno)
     * para compararlo con {@code tally_<r>}.
     *
     * @return JSON: {@code {"hashes":["hex1","hex2",...],"count":N,"round":<r>}};
     *         lista vacía si la ronda no existe o aún no se agrega.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryUpdateHashes(Context ctx, String roundIdStr) {
        ChaincodeStub stub = ctx.getStub();
        long roundId = Long.parseLong(roundIdStr);
        List<String> hashes = keys.readUpdateHashes(stub, roundId);

        StringBuilder sb = new StringBuilder("{\"hashes\":[");
        for (int i = 0; i < hashes.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(hashes.get(i)).append("\"");
        }
        sb.append("],\"count\":").append(hashes.size())
          .append(",\"round\":").append(roundId).append("}");
        return sb.toString();
    }

    /**
     * Información para que los clientes coordinen la agregación sin hablar
     * entre sí: si la ronda ya se agregó, cuántos updates hay frente a los K
     * esperados y la lista ordenada de {@code anon_cid}, cuyo primer elemento
     * es el líder.
     *
     * <p>Se invoca con {@code evaluateTransaction}, que no genera bloque, así que
     * el range read sobre {@code nullifier_<r>_*} no queda sujeto a la
     * validación de PHANTOM_READ.</p>
     *
     * @return JSON: {@code {"model_aggregated":bool,"updates_count":N,
     *         "expected_clients":K,"anon_cids":["hex1","hex2",...]}}, con la
     *         lista en orden lexicográfico ascendente.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getRoundLeaderInfo(Context ctx, String roundIdStr) {
        ChaincodeStub stub = ctx.getStub();
        long roundId = Long.parseLong(roundIdStr);

        boolean modelAggregated = keys.tryReadModelOptional(stub, roundId + 1) != null;
        List<String> anonCidsSorted = keys.readAllAnonCidHexesSorted(stub, roundId);
        int updatesCount = anonCidsSorted.size();
        int expectedClients = keys.readTotalClients(stub);

        StringBuilder sb = new StringBuilder("{\"model_aggregated\":")
            .append(modelAggregated)
            .append(",\"updates_count\":").append(updatesCount)
            .append(",\"expected_clients\":").append(expectedClients)
            .append(",\"anon_cids\":[");
        for (int i = 0; i < anonCidsSorted.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(anonCidsSorted.get(i)).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }
}
