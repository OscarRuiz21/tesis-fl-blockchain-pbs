package org.fl.flprotocol;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.flprotocol.crypto.CryptoCodec;
import org.fl.flprotocol.crypto.HashToCurve;
import org.fl.flprotocol.crypto.ModelSigner;
import org.fl.flprotocol.crypto.PBSDomainParams;
import org.fl.flprotocol.crypto.ProtocolHash;
import org.fl.flprotocol.serialization.ModelSerializer;
import org.fl.flprotocol.storage.ErrorCodes;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests del contrato con un stub de Fabric simulado.
 *
 * <p>{@code generateValidSubmission} recorre la emisión completa de la
 * credencial: compromisos del AS (A, B), cegado con t_1..t_4, respuesta del AS,
 * descegado a (ρ, ω, σ, δ) y firma de autoría ECDSA sobre Δw con anon_sk.</p>
 */
class FLProtocolChaincodeTest {

    private static PBSDomainParams params;
    private static SecureRandom random;

    private FLProtocolChaincode chaincode;
    private Context ctx;
    private ChaincodeStub stub;

    @BeforeAll
    static void globalSetup() {
        params = PBSDomainParams.p256();
        random = new SecureRandom();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        chaincode = new FLProtocolChaincode();
        ctx = mock(Context.class);
        stub = mock(ChaincodeStub.class);
        when(ctx.getStub()).thenReturn(stub);

        QueryResultsIterator<KeyValue> emptyIter = mock(QueryResultsIterator.class);
        when(emptyIter.iterator()).thenReturn(Collections.emptyIterator());
        when(stub.getStateByRange(anyString(), anyString())).thenReturn(emptyIter);
    }

    record SubmissionVector(
            String anonCidB64, String rhoB64, String omegaB64, String sigmaB64,
            String deltaB64, String nullifierHex, String sigModelB64,
            String xasB64, byte[] updateBytes) {}

    private SubmissionVector generateValidSubmission(long roundId, float[] modelUpdate) {
        BigInteger xAS = CryptoCodec.randomScalar(params.n(), random);
        ECPoint XAS = params.G().multiply(xAS).normalize();

        BigInteger anonSk = CryptoCodec.randomScalar(params.n(), random);
        ECPoint anonCid = params.G().multiply(anonSk).normalize();

        ECPoint Zr = HashToCurve.compute(roundId, params);

        BigInteger u = CryptoCodec.randomScalar(params.n(), random);
        BigInteger sAux = CryptoCodec.randomScalar(params.n(), random);
        BigInteger dAux = CryptoCodec.randomScalar(params.n(), random);
        ECPoint A = params.G().multiply(u).normalize();
        ECPoint B = params.G().multiply(sAux).add(Zr.multiply(dAux)).normalize();

        BigInteger t1 = CryptoCodec.randomScalar(params.n(), random);
        BigInteger t2 = CryptoCodec.randomScalar(params.n(), random);
        BigInteger t3 = CryptoCodec.randomScalar(params.n(), random);
        BigInteger t4 = CryptoCodec.randomScalar(params.n(), random);

        ECPoint alpha = A.add(params.G().multiply(t1)).add(XAS.multiply(t2)).normalize();
        ECPoint beta  = B.add(params.G().multiply(t3)).add(Zr.multiply(t4)).normalize();
        BigInteger eps = ProtocolHash.computeAbeOkamotoChallenge(alpha, beta, Zr, anonCid);
        BigInteger e = eps.subtract(t2).subtract(t4).mod(params.n());

        BigInteger c = e.subtract(dAux).mod(params.n());
        BigInteger rAns = u.subtract(c.multiply(xAS)).mod(params.n());

        BigInteger rho   = rAns.add(t1).mod(params.n());
        BigInteger omega = c.add(t2).mod(params.n());
        BigInteger sigma = sAux.add(t3).mod(params.n());
        BigInteger delta = dAux.add(t4).mod(params.n());

        byte[] updateBytes = ModelSerializer.serializeFloats(modelUpdate);
        byte[] sigModel = ModelSigner.sign(anonSk, updateBytes, params, random);

        String nullifierHex = HexFormat.of().formatHex(anonCid.getEncoded(false));

        return new SubmissionVector(
            CryptoCodec.encodePoint(anonCid),
            CryptoCodec.encodeScalar(rho),
            CryptoCodec.encodeScalar(omega),
            CryptoCodec.encodeScalar(sigma),
            CryptoCodec.encodeScalar(delta),
            nullifierHex,
            Base64.getEncoder().encodeToString(sigModel),
            CryptoCodec.encodePoint(XAS),
            updateBytes);
    }

    private String serializeModel(float[] model) {
        return ModelSerializer.toBase64(ModelSerializer.serializeFloats(model));
    }

    private float[] deserializeModel(String b64) {
        return ModelSerializer.deserializeFloats(ModelSerializer.fromBase64(b64));
    }

    @SuppressWarnings("unchecked")
    private void mockRangeQueryMultiUse(String startKey, String endKey, LinkedHashMap<String, String> entries) {
        when(stub.getStateByRange(startKey, endKey)).thenAnswer(invocation -> {
            QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
            List<KeyValue> kvList = new ArrayList<>();
            for (var entry : entries.entrySet()) {
                KeyValue kv = mock(KeyValue.class);
                when(kv.getKey()).thenReturn(entry.getKey());
                when(kv.getStringValue()).thenReturn(entry.getValue());
                kvList.add(kv);
            }
            when(iter.iterator()).thenReturn(kvList.iterator());
            return iter;
        });
    }

    private void setupStubForRound1(String xasB64, String nullifierHex, int numParams) {
        setupStubForRound1(xasB64, nullifierHex, false, numParams);
    }

    private void setupStubForRound1(String xasB64, String nullifierHex, boolean skipPbs, int numParams) {
        when(stub.getStringState("round_status")).thenReturn("1");
        when(stub.getStringState("nullifier_1_" + nullifierHex)).thenReturn("");
        when(stub.getStringState("as_public_key")).thenReturn(xasB64);
        when(stub.getStringState("skip_pbs")).thenReturn(skipPbs ? "true" : "false");
        when(stub.getStringState("num_params")).thenReturn(String.valueOf(numParams));
        // K=1 salvo que el test lo cambie.
        when(stub.getStringState("total_clients")).thenReturn("1");
    }

    private String submitWithVector(SubmissionVector v, String updateB64, String roundIdStr) {
        return chaincode.submitUpdate(ctx,
            v.anonCidB64(), v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
            updateB64, v.sigModelB64(), roundIdStr);
    }

    @Nested
    class InitProtocolTests {

        @Test
        void testInitProtocol_setsAllKeys() {
            String xasB64 = CryptoCodec.encodePoint(params.G().multiply(BigInteger.valueOf(42)).normalize());
            String modelB64 = serializeModel(new float[]{1.0f, 2.0f, 3.0f});

            chaincode.initProtocol(ctx, xasB64, modelB64, "3", "fedavg", "false", "3");

            verify(stub).putStringState("as_public_key", xasB64);
            verify(stub).putStringState("model_round_1", modelB64);
            verify(stub).putStringState("round_status", "1");
            verify(stub).putStringState("total_clients", "3");
            verify(stub).putStringState("aggregation_strategy", "fedavg");
            verify(stub).putStringState("skip_pbs", "false");
            verify(stub).putStringState("num_params", "3");
        }

        @Test
        void testInitProtocol_persistsNumParams() {
            String xasB64 = CryptoCodec.encodePoint(params.G().normalize());
            String modelB64 = serializeModel(new float[26]); // regresión logística de 26 parámetros

            chaincode.initProtocol(ctx, xasB64, modelB64, "2", "fedavg", "false", "26");

            verify(stub).putStringState("num_params", "26");
        }

        @Test
        void testInitProtocol_invalidStrategy_rejected() {
            String xasB64 = CryptoCodec.encodePoint(params.G().normalize());
            String modelB64 = serializeModel(new float[]{1.0f});

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                    () -> chaincode.initProtocol(ctx, xasB64, modelB64, "2", "unknown", "false", "1"));
            assertTrue(ex.getMessage().startsWith(ErrorCodes.UNKNOWN_STRATEGY + ":"));
        }

        @Test
        void testInitProtocol_invalidTotalClients_rejected() {
            String xasB64 = CryptoCodec.encodePoint(params.G().normalize());
            String modelB64 = serializeModel(new float[]{1.0f});

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                    () -> chaincode.initProtocol(ctx, xasB64, modelB64, "0", "fedavg", "false", "1"));
            assertTrue(ex.getMessage().startsWith(ErrorCodes.INVALID_TOTAL_CLIENTS + ":"));
        }

        @Test
        void testInitProtocol_invalidNumParams_rejected() {
            // El coordinator ya valida el YAML, pero el contrato no confía en él.
            String xasB64 = CryptoCodec.encodePoint(params.G().normalize());
            String modelB64 = serializeModel(new float[]{1.0f});

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                    () -> chaincode.initProtocol(ctx, xasB64, modelB64, "2", "fedavg", "false", "0"));
            assertTrue(ex.getMessage().startsWith(ErrorCodes.INVALID_NUM_PARAMS + ":"));
        }

        @Test
        void testInitProtocol_skipPbsStored() {
            String xasB64 = CryptoCodec.encodePoint(params.G().normalize());
            String modelB64 = serializeModel(new float[]{1.0f});

            chaincode.initProtocol(ctx, xasB64, modelB64, "2", "fedavg", "true", "1");

            verify(stub).putStringState("skip_pbs", "true");
        }

        @Test
        void testInitProtocol_storesModelHash() {
            String xasB64 = CryptoCodec.encodePoint(params.G().normalize());
            float[] model = {1.0f, 2.0f, 3.0f};
            byte[] modelBytes = ModelSerializer.serializeFloats(model);
            String modelB64 = ModelSerializer.toBase64(modelBytes);
            String expectedHash = ProtocolHash.sha256Hex(modelBytes);

            chaincode.initProtocol(ctx, xasB64, modelB64, "3", "fedavg", "false", "3");

            verify(stub).putStringState("model_hash_1", expectedHash);
        }
    }

    @Nested
    class SubmitUpdateValidTests {

        @Test
        void testSubmitUpdate_validSubmission_succeeds() {
            float[] update = {0.1f, 0.2f};
            SubmissionVector v = generateValidSubmission(1L, update);
            setupStubForRound1(v.xasB64(), v.nullifierHex(), update.length);

            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());
            String result = submitWithVector(v, updateB64, "1");

            assertTrue(result.contains("\"status\":\"ok\""));
            assertTrue(result.contains("\"sig_model_verify_ms\""));
            assertTrue(result.contains("\"pbs_verify_ms\""));
            assertTrue(result.contains("\"state_write_ms\""));
            assertTrue(result.contains("\"total_ms\""));
            assertTrue(result.contains("\"updates_received\""));
            assertTrue(result.contains("\"expected_clients\""));
            assertTrue(result.contains("\"is_last\""));

            verify(stub).putStringState(eq("update_1_" + v.nullifierHex()), eq(updateB64));
            verify(stub).putStringState(eq("nullifier_1_" + v.nullifierHex()), eq(v.anonCidB64()));
        }
    }

    @Nested
    class SubmitUpdateShapeTests {

        @Test
        void testSubmitUpdate_rejectsWrongShape() {
            // 2 floats contra num_params = 3; se rechaza antes de la cripto.
            float[] update = {0.1f, 0.2f};
            SubmissionVector v = generateValidSubmission(1L, update);
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 3);

            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> submitWithVector(v, updateB64, "1"));
            assertEquals(ErrorCodes.INVALID_NUM_PARAMS_SHAPE + ":2:3", ex.getMessage());
        }

        @Test
        void testSubmitUpdate_rejectsNonMultipleOfFour() {
            // Longitud que no es múltiplo de 4 bytes.
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);

            String malformedB64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> submitWithVector(v, malformedB64, "1"));
            assertTrue(ex.getMessage().startsWith(ErrorCodes.INVALID_NUM_PARAMS_SHAPE + ":"));
        }
    }

    // submitUpdate no cuenta los updates: ese range read provocaba
    // PHANTOM_READ_CONFLICT con K >= 10. Quién agrega se decide con
    // getRoundLeaderInfo (EVALUATE), y el JSON lleva valores fijos
    // updates_received = -1 e is_last = false.

    @Nested
    class SubmitUpdateResponseTests {

        @Test
        void submitUpdate_response_returnsSentinelsForOrchestrationFields() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.5f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);
            when(stub.getStringState("total_clients")).thenReturn("5");

            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());
            String result = submitWithVector(v, updateB64, "1");

            assertTrue(result.contains("\"status\":\"ok\""), "response: " + result);
            assertTrue(result.contains("\"updates_received\":-1"),
                "expected sentinel updates_received=-1, got: " + result);
            assertTrue(result.contains("\"expected_clients\":5"),
                "expected_clients should mirror configured K, got: " + result);
            assertTrue(result.contains("\"is_last\":false"),
                "expected sentinel is_last=false, got: " + result);
        }

        @Test
        void submitUpdate_response_doesNotPerformRangeReadOnUpdates() {
            // Ese range read sobre "update_1_*" causaba el PHANTOM_READ.
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.5f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);
            when(stub.getStringState("total_clients")).thenReturn("5");

            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());
            submitWithVector(v, updateB64, "1");

            verify(stub, never()).getStateByRange(eq("update_1_"), eq("update_1_~"));
        }
    }

    @Nested
    class SubmitUpdatePbsInvalidTests {

        @Test
        void testSubmitUpdate_tamperedRho_rejected() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);

            BigInteger bumped = CryptoCodec.decodeScalar(v.rhoB64(), params.n())
                .add(BigInteger.ONE).mod(params.n());
            String tamperedRho = CryptoCodec.encodeScalar(bumped);
            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.submitUpdate(ctx,
                    v.anonCidB64(), tamperedRho, v.omegaB64(), v.sigmaB64(), v.deltaB64(),
                    updateB64, v.sigModelB64(), "1"));
            assertEquals(ErrorCodes.INVALID_PBS_SIGNATURE, ex.getMessage());
        }

        @Test
        void testSubmitUpdate_tamperedOmega_rejected() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);

            BigInteger bumped = CryptoCodec.decodeScalar(v.omegaB64(), params.n())
                .add(BigInteger.ONE).mod(params.n());
            String tamperedOmega = CryptoCodec.encodeScalar(bumped);
            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.submitUpdate(ctx,
                    v.anonCidB64(), v.rhoB64(), tamperedOmega, v.sigmaB64(), v.deltaB64(),
                    updateB64, v.sigModelB64(), "1"));
            assertEquals(ErrorCodes.INVALID_PBS_SIGNATURE, ex.getMessage());
        }

        @Test
        void testSubmitUpdate_tamperedSigma_rejected() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);

            BigInteger bumped = CryptoCodec.decodeScalar(v.sigmaB64(), params.n())
                .add(BigInteger.ONE).mod(params.n());
            String tamperedSigma = CryptoCodec.encodeScalar(bumped);
            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.submitUpdate(ctx,
                    v.anonCidB64(), v.rhoB64(), v.omegaB64(), tamperedSigma, v.deltaB64(),
                    updateB64, v.sigModelB64(), "1"));
            assertEquals(ErrorCodes.INVALID_PBS_SIGNATURE, ex.getMessage());
        }

        @Test
        void testSubmitUpdate_tamperedDelta_rejected() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);

            BigInteger bumped = CryptoCodec.decodeScalar(v.deltaB64(), params.n())
                .add(BigInteger.ONE).mod(params.n());
            String tamperedDelta = CryptoCodec.encodeScalar(bumped);
            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.submitUpdate(ctx,
                    v.anonCidB64(), v.rhoB64(), v.omegaB64(), v.sigmaB64(), tamperedDelta,
                    updateB64, v.sigModelB64(), "1"));
            assertEquals(ErrorCodes.INVALID_PBS_SIGNATURE, ex.getMessage());
        }

        @Test
        void testSubmitUpdate_differentASKey_rejected() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});

            BigInteger differentXAS = CryptoCodec.randomScalar(params.n(), random);
            ECPoint differentXASPoint = params.G().multiply(differentXAS).normalize();
            String differentXASB64 = CryptoCodec.encodePoint(differentXASPoint);

            when(stub.getStringState("round_status")).thenReturn("1");
            when(stub.getStringState("nullifier_1_" + v.nullifierHex())).thenReturn("");
            when(stub.getStringState("as_public_key")).thenReturn(differentXASB64);
            when(stub.getStringState("skip_pbs")).thenReturn("false");
            when(stub.getStringState("num_params")).thenReturn("1");
            when(stub.getStringState("total_clients")).thenReturn("1");

            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> submitWithVector(v, updateB64, "1"));
            assertEquals(ErrorCodes.INVALID_PBS_SIGNATURE, ex.getMessage());
        }

        @Test
        void testSubmitUpdate_zeroScalar_rejected() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);

            byte[] zeroBytes = new byte[32];
            String zeroB64 = Base64.getEncoder().encodeToString(zeroBytes);
            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.submitUpdate(ctx,
                    v.anonCidB64(), zeroB64, v.omegaB64(), v.sigmaB64(), v.deltaB64(),
                    updateB64, v.sigModelB64(), "1"));
            assertEquals(ErrorCodes.INVALID_PBS_SIGNATURE, ex.getMessage());
        }
    }

    @Nested
    class SubmitUpdateP6InvalidTests {

        @Test
        void testSubmitUpdate_mutatedUpdate_p6Fails() {
            // El cliente firmó X y un atacante en tránsito lo sustituye por Y,
            // del mismo tamaño para pasar la validación de dimensión.
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f, 0.2f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 2);

            byte[] mutated = ModelSerializer.serializeFloats(new float[]{99.0f, 99.0f});
            String mutatedB64 = Base64.getEncoder().encodeToString(mutated);

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> submitWithVector(v, mutatedB64, "1"));
            assertEquals(ErrorCodes.INVALID_SIG_MODEL, ex.getMessage());
        }

        @Test
        void testSubmitUpdate_sigModelFromOtherClient_rejected() {
            SubmissionVector v1 = generateValidSubmission(1L, new float[]{0.1f});
            SubmissionVector v2 = generateValidSubmission(1L, new float[]{0.1f});
            setupStubForRound1(v1.xasB64(), v1.nullifierHex(), 1);

            String updateB64 = Base64.getEncoder().encodeToString(v1.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.submitUpdate(ctx,
                    v1.anonCidB64(), v1.rhoB64(), v1.omegaB64(), v1.sigmaB64(), v1.deltaB64(),
                    updateB64, v2.sigModelB64(), "1"));
            assertEquals(ErrorCodes.INVALID_SIG_MODEL, ex.getMessage());
        }

        @Test
        void testSubmitUpdate_malformedSigModel_rejected() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);

            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());
            String malformedSig = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4});

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.submitUpdate(ctx,
                    v.anonCidB64(), v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
                    updateB64, malformedSig, "1"));
            assertEquals(ErrorCodes.INVALID_SIG_MODEL, ex.getMessage());
        }

        @Test
        void testSubmitUpdate_emptySigModel_rejected() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});
            setupStubForRound1(v.xasB64(), v.nullifierHex(), 1);

            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.submitUpdate(ctx,
                    v.anonCidB64(), v.rhoB64(), v.omegaB64(), v.sigmaB64(), v.deltaB64(),
                    updateB64, "", "1"));
            assertEquals(ErrorCodes.INVALID_SIG_MODEL, ex.getMessage());
        }
    }

    @Nested
    class SubmitUpdateNonCryptoTests {

        @Test
        void testSubmitUpdate_duplicateNullifier_rejected() {
            SubmissionVector v = generateValidSubmission(1L, new float[]{0.1f});

            when(stub.getStringState("round_status")).thenReturn("1");
            // El nulificador se valida después de P6 y de la PBS, así que la
            // credencial tiene que ser válida para llegar a esa comprobación.
            when(stub.getStringState("as_public_key")).thenReturn(v.xasB64());
            when(stub.getStringState("skip_pbs")).thenReturn("false");
            when(stub.getStringState("num_params")).thenReturn("1");
            // Cualquier valor no vacío marca el nulificador como consumido.
            when(stub.getStringState("nullifier_1_" + v.nullifierHex())).thenReturn(v.anonCidB64());

            String updateB64 = Base64.getEncoder().encodeToString(v.updateBytes());

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> submitWithVector(v, updateB64, "1"));
            assertTrue(ex.getMessage().startsWith(ErrorCodes.DUPLICATE_NULLIFIER + ":"));
        }

        @Test
        void testSubmitUpdate_wrongRound_rejected() {
            // La ronda se valida antes que todo lo demás, así que basta con
            // argumentos de relleno.
            when(stub.getStringState("round_status")).thenReturn("1");
            String updateB64 = Base64.getEncoder().encodeToString(new byte[]{0});

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.submitUpdate(ctx,
                    "dW51c2Vk", "dW51c2Vk", "dW51c2Vk", "dW51c2Vk", "dW51c2Vk",
                    updateB64, "dW51c2Vk", "5"));
            assertTrue(ex.getMessage().startsWith(ErrorCodes.ROUND_NOT_ACTIVE + ":"));
        }
    }

    @Nested
    class SubmitUpdateSkipPbsTests {

        @Test
        void testSubmitUpdate_skipPbsTrue_acceptsTrivialCredential() {
            // Con skip_pbs se omiten P6 y PBS, pero el contrato sigue derivando
            // anonCidHex y validando la dimensión: hace falta un punto real y
            // un update bien dimensionado.
            ECPoint dummyAnonCid = params.G().multiply(BigInteger.valueOf(7)).normalize();
            String anonCidB64 = CryptoCodec.encodePoint(dummyAnonCid);
            String anonCidHex = HexFormat.of().formatHex(dummyAnonCid.getEncoded(false));

            when(stub.getStringState("round_status")).thenReturn("1");
            when(stub.getStringState("nullifier_1_" + anonCidHex)).thenReturn("");
            when(stub.getStringState("skip_pbs")).thenReturn("true");
            when(stub.getStringState("num_params")).thenReturn("1");
            when(stub.getStringState("total_clients")).thenReturn("1");

            String updateB64 = serializeModel(new float[]{0.5f});
            String result = chaincode.submitUpdate(ctx,
                anonCidB64, "dW51c2Vk", "dW51c2Vk", "dW51c2Vk", "dW51c2Vk",
                updateB64, "", "1");

            assertTrue(result.contains("\"status\":\"ok\""));
            assertTrue(result.contains("\"sig_model_verify_ms\":0"));
            assertTrue(result.contains("\"pbs_verify_ms\":0"));

            verify(stub).putStringState("update_1_" + anonCidHex, updateB64);
        }
    }

    @Nested
    class AggregateRoundTests {

        private void setupAggregation(float[] model, float[][] updates, int totalClients,
                                      String strategy, long roundId) {
            when(stub.getStringState("round_status")).thenReturn(String.valueOf(roundId));
            when(stub.getStringState("total_clients")).thenReturn(String.valueOf(totalClients));
            when(stub.getStringState("aggregation_strategy")).thenReturn(strategy);
            when(stub.getStringState("model_round_" + roundId)).thenReturn(serializeModel(model));

            LinkedHashMap<String, String> updateEntries = new LinkedHashMap<>();
            for (int i = 0; i < updates.length; i++) {
                String key = "update_" + roundId + "_" + String.format("%064x", i);
                updateEntries.put(key, serializeModel(updates[i]));
            }
            String startKey = "update_" + roundId + "_";
            String endKey = "update_" + roundId + "_~";
            mockRangeQueryMultiUse(startKey, endKey, updateEntries);
        }

        @Test
        void testAggregateRound_fedAvg_correctResult() {
            float[] model = {1.0f, 2.0f, 3.0f};
            float[][] updates = {{0.2f, 0.4f, 0.6f}, {0.4f, 0.8f, 1.2f}};
            setupAggregation(model, updates, 2, "fedavg", 1L);

            String result = chaincode.aggregateRound(ctx, "1");

            assertTrue(result.contains("\"status\":\"aggregated\""));
            assertTrue(result.contains("\"round\":1"));
            assertTrue(result.contains("\"updatesUsed\":2"));
            assertTrue(result.contains("\"strategy\":\"fedavg\""));
            assertTrue(result.contains("\"newRound\":2"));
            assertTrue(result.contains("\"timings\""));

            var captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(stub).putStringState(eq("model_round_2"), captor.capture());

            float[] newModel = deserializeModel(captor.getValue());
            assertEquals(3, newModel.length);
            assertEquals(1.3f, newModel[0], 1e-5f);
            assertEquals(2.6f, newModel[1], 1e-5f);
            assertEquals(3.9f, newModel[2], 1e-5f);
        }

        @Test
        void testAggregateRound_fedProx_identicalToFedAvg() {
            float[] model = {1.0f, 2.0f};
            float[][] updates = {{0.5f, 1.0f}, {1.5f, 3.0f}};
            setupAggregation(model, updates, 2, "fedprox", 1L);

            chaincode.aggregateRound(ctx, "1");

            var captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(stub).putStringState(eq("model_round_2"), captor.capture());

            float[] newModel = deserializeModel(captor.getValue());
            assertEquals(2.0f, newModel[0], 1e-5f);
            assertEquals(4.0f, newModel[1], 1e-5f);
        }

        @Test
        void testAggregateRound_fedMedian_resistsOutlier() {
            float[] model = {0.0f};
            float[][] updates = {{1.0f}, {1.0f}, {100.0f}};
            setupAggregation(model, updates, 3, "fedmedian", 1L);

            chaincode.aggregateRound(ctx, "1");

            var captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(stub).putStringState(eq("model_round_2"), captor.capture());

            float[] newModel = deserializeModel(captor.getValue());
            assertEquals(1.0f, newModel[0], 1e-5f);
        }

        @Test
        void testAggregateRound_notEnoughUpdates_rejected() {
            when(stub.getStringState("round_status")).thenReturn("1");
            when(stub.getStringState("total_clients")).thenReturn("3");
            when(stub.getStringState("aggregation_strategy")).thenReturn("fedavg");

            LinkedHashMap<String, String> oneUpdate = new LinkedHashMap<>();
            oneUpdate.put("update_1_abc", serializeModel(new float[]{1.0f}));
            mockRangeQueryMultiUse("update_1_", "update_1_~", oneUpdate);

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.aggregateRound(ctx, "1"));
            assertTrue(ex.getMessage().startsWith(ErrorCodes.NOT_ENOUGH_UPDATES + ":"));
        }

        @Test
        void testAggregateRound_wrongRound_rejected() {
            when(stub.getStringState("round_status")).thenReturn("1");

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.aggregateRound(ctx, "5"));
            assertTrue(ex.getMessage().startsWith(ErrorCodes.ROUND_NOT_ACTIVE + ":"));
        }

        @Test
        void testAggregateRound_tallyHashCorrect() {
            float[] model = {0.0f};
            float[][] updates = {{1.0f}, {2.0f}};
            setupAggregation(model, updates, 2, "fedavg", 1L);

            chaincode.aggregateRound(ctx, "1");

            byte[] raw1 = ModelSerializer.serializeFloats(new float[]{1.0f});
            byte[] raw2 = ModelSerializer.serializeFloats(new float[]{2.0f});
            String expectedTally = ProtocolHash.computeTallyHashHex(List.of(raw1, raw2));

            verify(stub).putStringState("tally_1", expectedTally);
        }

        @Test
        void testAggregateRound_storesModelHash() {
            // model_hash_2 debe ser SHA-256 de los bytes guardados en model_round_2.
            float[] model = {0.0f};
            float[][] updates = {{1.0f}, {2.0f}};
            setupAggregation(model, updates, 2, "fedavg", 1L);

            chaincode.aggregateRound(ctx, "1");

            var modelCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(stub).putStringState(eq("model_round_2"), modelCaptor.capture());
            String expectedHash = ProtocolHash.sha256Hex(
                ModelSerializer.fromBase64(modelCaptor.getValue()));

            verify(stub).putStringState("model_hash_2", expectedHash);
        }

        @Test
        void testAggregateRound_advancesRound() {
            float[] model = {0.0f};
            float[][] updates = {{1.0f}};
            setupAggregation(model, updates, 1, "fedavg", 1L);

            chaincode.aggregateRound(ctx, "1");

            verify(stub).putStringState("round_status", "2");
        }

        @Test
        void testAggregateRound_idempotent() {
            float[] model = {0.0f};
            float[][] updates = {{1.0f}};
            setupAggregation(model, updates, 1, "fedavg", 1L);

            chaincode.aggregateRound(ctx, "1");

            when(stub.getStringState("round_status")).thenReturn("2");

            ChaincodeException ex = assertThrows(ChaincodeException.class,
                () -> chaincode.aggregateRound(ctx, "1"));
            assertTrue(ex.getMessage().startsWith(ErrorCodes.ROUND_NOT_ACTIVE + ":"));
        }

        @Test
        void testAggregateRound_cleansUpWorldState() {
            float[] model = {0.0f};
            float[][] updates = {{1.0f}};
            setupAggregation(model, updates, 1, "fedavg", 1L);

            chaincode.aggregateRound(ctx, "1");

            verify(stub).delState(String.format("update_1_%064x", 0));
            verify(stub).delState("model_round_1");
        }

        @Test
        void testAggregateRound_preservesNullifiers() {
            float[] model = {0.0f};
            float[][] updates = {{1.0f}};
            setupAggregation(model, updates, 1, "fedavg", 1L);

            chaincode.aggregateRound(ctx, "1");

            verify(stub, never()).delState(argThat(key -> key.startsWith("nullifier_")));
        }
    }

    @Nested
    class QueryTests {

        @Test
        void testQueryUpdates_returnsAllUpdates() {
            LinkedHashMap<String, String> entries = new LinkedHashMap<>();
            entries.put("update_1_aaa", serializeModel(new float[]{1.0f}));
            entries.put("update_1_bbb", serializeModel(new float[]{2.0f}));
            mockRangeQueryMultiUse("update_1_", "update_1_~", entries);

            String result = chaincode.queryUpdates(ctx, "1");

            assertTrue(result.contains("\"count\":2"));
            assertTrue(result.contains("\"updates\":["));
        }

        @Test
        void testQueryGlobalModel_returnsModelHash() {
            float[] model = {1.0f, 2.0f};
            byte[] modelBytes = ModelSerializer.serializeFloats(model);
            String modelB64 = ModelSerializer.toBase64(modelBytes);
            String modelHash = ProtocolHash.sha256Hex(modelBytes);
            when(stub.getStringState("model_round_1")).thenReturn(modelB64);
            when(stub.getStringState("model_hash_1")).thenReturn(modelHash);

            String result = chaincode.queryGlobalModel(ctx, "1");

            assertTrue(result.contains("\"model_hash\":\"" + modelHash + "\""),
                "expected model_hash in response, got: " + result);
        }

        @Test
        void testQueryRoundStatus_returnsConfig() {
            when(stub.getStringState("round_status")).thenReturn("3");
            when(stub.getStringState("total_clients")).thenReturn("5");
            when(stub.getStringState("aggregation_strategy")).thenReturn("fedmedian");

            String result = chaincode.queryRoundStatus(ctx);

            assertTrue(result.contains("\"active_round\":3"));
            assertTrue(result.contains("\"total_clients\":5"));
            assertTrue(result.contains("\"strategy\":\"fedmedian\""));
        }
    }
}
