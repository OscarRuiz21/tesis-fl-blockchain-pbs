package org.fl.flprotocol.storage;

import org.hyperledger.fabric.shim.ChaincodeException;

/**
 * Códigos de error del contrato con la forma {@code TIPO[:ctx1[:ctx2...]]}, con
 * contextos numéricos o hex para que el cliente los separe con un regex simple.
 * El cliente traduce {@code ROUND_NOT_ACTIVE}, {@code DUPLICATE_NULLIFIER} e
 * {@code INVALID_PBS_SIGNATURE}/{@code INVALID_SIG_MODEL} a excepciones propias;
 * el resto llega como {@code ChaincodeProtocolException}.
 */
public final class ErrorCodes {

    public static final String INVALID_TOTAL_CLIENTS = "INVALID_TOTAL_CLIENTS";
    public static final String INVALID_NUM_PARAMS = "INVALID_NUM_PARAMS";
    public static final String INVALID_NUM_PARAMS_SHAPE = "INVALID_NUM_PARAMS_SHAPE";
    public static final String UNKNOWN_STRATEGY = "UNKNOWN_STRATEGY";
    public static final String ROUND_NOT_ACTIVE = "ROUND_NOT_ACTIVE";
    public static final String DUPLICATE_NULLIFIER = "DUPLICATE_NULLIFIER";
    public static final String INVALID_PBS_SIGNATURE = "INVALID_PBS_SIGNATURE";
    public static final String INVALID_SIG_MODEL = "INVALID_SIG_MODEL";
    public static final String NOT_ENOUGH_UPDATES = "NOT_ENOUGH_UPDATES";
    public static final String NO_MODEL_FOR_ROUND = "NO_MODEL_FOR_ROUND";

    private ErrorCodes() {}

    public static ChaincodeException invalidTotalClients(int n) {
        return new ChaincodeException(INVALID_TOTAL_CLIENTS + ":" + n);
    }

    public static ChaincodeException invalidNumParams(int n) {
        return new ChaincodeException(INVALID_NUM_PARAMS + ":" + n);
    }

    /** {@code got} es {@code length / 4} del update; {@code expected}, el {@code num_params} de {@code initProtocol}. */
    public static ChaincodeException invalidNumParamsShape(int got, int expected) {
        return new ChaincodeException(
            INVALID_NUM_PARAMS_SHAPE + ":" + got + ":" + expected);
    }

    public static ChaincodeException unknownStrategy(String name) {
        // El nombre llega del llamante; se sanitiza para no romper el formato del código.
        String safe = name == null ? "<null>" : name.replaceAll("[^a-zA-Z0-9_]", "_");
        return new ChaincodeException(UNKNOWN_STRATEGY + ":" + safe);
    }

    public static ChaincodeException roundNotActive(long requested, long active) {
        return new ChaincodeException(
            ROUND_NOT_ACTIVE + ":" + requested + ":" + active);
    }

    /** El contexto es el hex del {@code anon_cid} sin comprimir (130 caracteres). */
    public static ChaincodeException duplicateNullifier(String anonCidHex) {
        return new ChaincodeException(DUPLICATE_NULLIFIER + ":" + anonCidHex);
    }

    public static ChaincodeException invalidPbsSignature() {
        return new ChaincodeException(INVALID_PBS_SIGNATURE);
    }

    public static ChaincodeException invalidSigModel() {
        return new ChaincodeException(INVALID_SIG_MODEL);
    }

    public static ChaincodeException notEnoughUpdates(int received, int expected, long round) {
        return new ChaincodeException(
            NOT_ENOUGH_UPDATES + ":" + received + ":" + expected + ":" + round);
    }

    public static ChaincodeException noModelForRound(long round) {
        return new ChaincodeException(NO_MODEL_FOR_ROUND + ":" + round);
    }
}
