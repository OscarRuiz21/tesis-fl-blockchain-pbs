package org.fl.client.gateway;

import org.fl.client.exception.BlockchainUnavailableException;
import org.fl.client.model.AggregationResult;
import org.fl.client.model.AnonId;
import org.fl.client.model.Credential;
import org.fl.client.model.GlobalModel;
import org.fl.client.model.LeaderInfo;
import org.fl.client.model.ModelUpdate;
import org.fl.client.model.RoundId;
import org.fl.client.model.RoundStatus;
import org.fl.client.model.SubmitResponse;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.SubmitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Transacciones del cliente sobre el contrato. Quién dispara
 * {@link #tryAggregateRound} lo decide {@code RunRoundService} con la elección
 * de líder, no este gateway.
 */
@Component
public class BlockchainGateway {

    private static final Logger log = LoggerFactory.getLogger(BlockchainGateway.class);

    private final Contract contract;
    private final FabricErrorTranslator errorTranslator;
    private final ChaincodeResponseParser parser;
    private final SubmitPayloadMapper submitMapper;
    private final GlobalModelJsonMapper globalModelMapper;

    public BlockchainGateway(Contract contract,
                             FabricErrorTranslator errorTranslator,
                             ChaincodeResponseParser parser,
                             SubmitPayloadMapper submitMapper,
                             GlobalModelJsonMapper globalModelMapper) {
        this.contract = contract;
        this.errorTranslator = errorTranslator;
        this.parser = parser;
        this.submitMapper = submitMapper;
        this.globalModelMapper = globalModelMapper;
    }

    /** Etapa 2: envío anónimo de la actualización. */
    public SubmitResponse submitUpdate(
            AnonId anonCid, Credential credential, ModelUpdate update,
            byte[] sigModel, RoundId roundId) {
        var payload = submitMapper.toStrings(anonCid, credential, update, sigModel, roundId);
        // Hasta 5 intentos con backoff exponencial ante conflictos MVCC o
        // PHANTOM_READ entre los K clientes que envían a la vez. El número de
        // intentos va al CSV para separar el costo de los reintentos del costo
        // del chaincode.
        int maxAttempts = 5;
        long backoffMs = 200L;
        Throwable lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                byte[] response = contract.submitTransaction("submitUpdate",
                    payload.anonCidB64(),
                    payload.rhoB64(),
                    payload.omegaB64(),
                    payload.sigmaB64(),
                    payload.deltaB64(),
                    payload.updateB64(),
                    payload.sigModelB64(),
                    payload.roundIdStr());
                return parser.parseSubmitResponse(new String(response)).withAttempts(attempt);
            } catch (EndorseException e) {
                String msg = extractMessage(e);
                throw errorTranslator.translateChaincodeMessage(msg, anonCid, roundId);
            } catch (SubmitException | CommitException | CommitStatusException e) {
                lastError = e;
                if (errorTranslator.isRetryableConflict(e) && attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BlockchainUnavailableException(ie);
                    }
                    backoffMs *= 2;
                    continue;
                }
                throw errorTranslator.unavailable(e);
            } catch (Exception e) {
                throw new BlockchainUnavailableException(e);
            }
        }
        throw new BlockchainUnavailableException(lastError);
    }

    /**
     * Etapa 3: agregación verificable, invocada por el líder. La respuesta
     * trae el Tally Hash y los tiempos; el modelo de la ronda r+1 se consulta
     * aparte.
     */
    public AggregationResult tryAggregateRound(RoundId roundId) {
        try {
            byte[] response = contract.submitTransaction(
                "aggregateRound", String.valueOf(roundId.value()));
            String json = new String(response);

            GlobalModel newModel = queryGlobalModel(roundId.next());
            return parser.parseAggregateResponse(json, newModel);
        } catch (EndorseException e) {
            String msg = extractMessage(e);
            throw errorTranslator.translateChaincodeMessage(msg, null, roundId);
        } catch (CommitException ce) {
            if (errorTranslator.isMvccReadConflict(ce)) {
                // Otro candidato a líder agregó primero.
                log.warn("MVCC race lost on aggregateRound for round {}", roundId.value());
                throw errorTranslator.unavailable(ce);
            }
            throw errorTranslator.unavailable(ce);
        } catch (SubmitException | CommitStatusException e) {
            throw errorTranslator.unavailable(e);
        } catch (Exception e) {
            throw new BlockchainUnavailableException(e);
        }
    }

    public RoundStatus queryRoundStatus() {
        try {
            byte[] response = contract.evaluateTransaction("queryRoundStatus");
            return parser.parseRoundStatusResponse(new String(response));
        } catch (Exception e) {
            throw errorTranslator.unavailable(e);
        }
    }

    /**
     * Con {@code evaluateTransaction} el range read sobre
     * {@code nullifier_<r>_*} no entra a ningún read-set validado, así que no
     * puede provocar {@code PHANTOM_READ_CONFLICT}.
     */
    public LeaderInfo queryLeaderInfo(RoundId roundId) {
        try {
            byte[] response = contract.evaluateTransaction(
                "getRoundLeaderInfo", String.valueOf(roundId.value()));
            return parser.parseLeaderInfoResponse(new String(response));
        } catch (Exception e) {
            throw errorTranslator.unavailable(e);
        }
    }

    /** Hashes {@code h_r^k} ordenados para la auditoría P4/P5; vacía si la ronda no ha agregado. */
    public java.util.List<String> queryUpdateHashes(RoundId roundId) {
        try {
            byte[] response = contract.evaluateTransaction(
                "queryUpdateHashes", String.valueOf(roundId.value()));
            return parser.parseUpdateHashesResponse(new String(response));
        } catch (Exception e) {
            throw errorTranslator.unavailable(e);
        }
    }

    public GlobalModel queryGlobalModel(RoundId roundId) {
        try {
            byte[] response = contract.evaluateTransaction(
                "queryGlobalModel", String.valueOf(roundId.value()));
            return globalModelMapper.fromJson(new String(response), roundId);
        } catch (Exception e) {
            throw errorTranslator.unavailable(e);
        }
    }

    /** El código del chaincode ({@code "ROUND_NOT_ACTIVE:..."}) viaja en los details. */
    private static String extractMessage(GatewayException ex) {
        return ex.getDetails().stream()
            .findFirst()
            .map(d -> d.getMessage())
            .orElse(ex.getMessage());
    }
}
