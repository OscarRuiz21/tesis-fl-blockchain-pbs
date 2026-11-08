package org.fl.coordinator.gateway;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.math.ec.ECPoint;
import org.fl.coordinator.crypto.CryptoCodec;
import org.fl.coordinator.crypto.ModelSerializer;
import org.fl.coordinator.exception.BlockchainUnavailableException;
import org.fl.coordinator.model.GlobalModel;
import org.fl.coordinator.model.RoundId;
import org.fl.coordinator.model.RoundStatus;
import org.fl.coordinator.model.TallyHash;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.SubmitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Acceso del coordinator al contrato: {@code initProtocol} al inicio y luego
 * solo consultas. {@code submitUpdate} y {@code aggregateRound} no están aquí
 * porque los invocan los clientes.
 *
 * <p>Usa la misma condición de propiedad que el bean {@code Contract} de
 * AppConfig. Con {@code @ConditionalOnBean} el resultado dependía del orden en
 * que el escaneo encuentra las clases dentro del jar.</p>
 */
@Component
@ConditionalOnProperty(name = "fl.adapter.blockchain", havingValue = "fabric", matchIfMissing = true)
@RequiredArgsConstructor
public class BlockchainGateway {

    private static final Logger log = LoggerFactory.getLogger(BlockchainGateway.class);

    private final Contract contract;
    private final ChaincodeResponseParser parser;

    /** El chaincode guarda {@code numParams} y rechaza actualizaciones de otra dimensión. */
    public void initProtocol(
            ECPoint asPublicKey,
            GlobalModel initialModel,
            int totalClients,
            String aggregationStrategy,
            String anonymityMode,
            int numParams) {
        try {
            String asPubKeyB64 = CryptoCodec.encodePoint(asPublicKey);
            String modelB64 = ModelSerializer.toBase64(initialModel.bytes());
            // anonymity=none es la línea base: el contrato omite la verificación PBS.
            String skipPbs = "none".equalsIgnoreCase(anonymityMode) ? "true" : "false";
            contract.submitTransaction("initProtocol",
                asPubKeyB64,
                modelB64,
                String.valueOf(totalClients),
                aggregationStrategy,
                skipPbs,
                String.valueOf(numParams));
            log.info("Chaincode initProtocol succeeded with K={} strategy={} skipPbs={} numParams={}",
                totalClients, aggregationStrategy, skipPbs, numParams);
        } catch (EndorseException | SubmitException | CommitException | CommitStatusException e) {
            throw new BlockchainUnavailableException(e);
        } catch (Exception e) {
            throw new BlockchainUnavailableException(e);
        }
    }

    public RoundStatus queryRoundStatus() {
        try {
            byte[] response = contract.evaluateTransaction("queryRoundStatus");
            ChaincodeResponseParser.RoundStatusJson parsed =
                parser.parseRoundStatusResponse(new String(response));
            return new RoundStatus(
                parsed.activeRound(),
                parsed.totalClients(),
                parsed.strategyName());
        } catch (Exception e) {
            throw new BlockchainUnavailableException(e);
        }
    }

    public GlobalModel queryGlobalModel(RoundId roundId) {
        try {
            byte[] response = contract.evaluateTransaction(
                "queryGlobalModel", String.valueOf(roundId.value()));
            String json = new String(response);
            byte[] modelBytes = ModelSerializer.extractModelBytes(json);
            String tallyHex = extractField(json, "\"tally_hash\":\"");
            TallyHash previous = (roundId.value() == 1 || tallyHex.isEmpty())
                ? null : TallyHash.of(tallyHex);
            return GlobalModel.of(modelBytes, roundId, previous);
        } catch (Exception e) {
            throw new BlockchainUnavailableException(e);
        }
    }

    private static String extractField(String json, String key) {
        int start = json.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }
}
