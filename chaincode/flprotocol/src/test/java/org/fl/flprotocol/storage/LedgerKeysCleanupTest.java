package org.fl.flprotocol.storage;

import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests de {@code cleanupAll}: un experimento nuevo borra el estado dinámico
 * del anterior (nulificadores, tallies, modelos, updates) y deja las claves
 * estáticas, que {@code initialize()} sobrescribe.
 */
class LedgerKeysCleanupTest {

    private LedgerKeys keys;
    private ChaincodeStub stub;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        keys = new LedgerKeys();
        stub = mock(ChaincodeStub.class);

        // Por omisión todo range read viene vacío; cada test define sus prefijos.
        QueryResultsIterator<KeyValue> emptyIter = mock(QueryResultsIterator.class);
        when(emptyIter.iterator()).thenReturn(Collections.emptyIterator());
        when(stub.getStateByRange(anyString(), anyString())).thenReturn(emptyIter);
    }

    @Test
    void cleanupAll_emptyLedger_noDeletes() {
        keys.cleanupAll(stub);
        verify(stub, never()).delState(anyString());
    }

    @Test
    void cleanupAll_deletesAllNullifiers() {
        mockRangeQuery("nullifier_", "nullifier_~", Map.of(
            "nullifier_1_aaaa", "used",
            "nullifier_2_bbbb", "used",
            "nullifier_3_cccc", "used"
        ));

        keys.cleanupAll(stub);

        verify(stub).delState("nullifier_1_aaaa");
        verify(stub).delState("nullifier_2_bbbb");
        verify(stub).delState("nullifier_3_cccc");
    }

    @Test
    void cleanupAll_deletesAllAccumulatedPrefixes() {
        mockRangeQuery("model_round_", "model_round_~", Map.of(
            "model_round_5", "<base64>",
            "model_round_3", "<base64>"
        ));
        mockRangeQuery("tally_", "tally_~", Map.of(
            "tally_1", "abc123",
            "tally_2", "def456"
        ));
        mockRangeQuery("update_", "update_~", Map.of(
            "update_5_aaaa", "<b64>"
        ));
        mockRangeQuery("nullifier_", "nullifier_~", Map.of(
            "nullifier_5_aaaa", "<anonCidB64>"
        ));

        keys.cleanupAll(stub);

        verify(stub).delState("model_round_5");
        verify(stub).delState("model_round_3");
        verify(stub).delState("tally_1");
        verify(stub).delState("tally_2");
        verify(stub).delState("update_5_aaaa");
        verify(stub).delState("nullifier_5_aaaa");

        verify(stub, times(6)).delState(anyString());
    }

    @Test
    void cleanupAll_doesNotTouchStaticKeys() {
        // Ningún prefijo de cleanupAll cubre las claves estáticas, num_params
        // incluida.
        keys.cleanupAll(stub);

        verify(stub, never()).delState("as_public_key");
        verify(stub, never()).delState("round_status");
        verify(stub, never()).delState("total_clients");
        verify(stub, never()).delState("aggregation_strategy");
        verify(stub, never()).delState("skip_pbs");
        verify(stub, never()).delState("num_params");
    }

    @Test
    void cleanupAll_iteratesExactlySixPrefixes() {
        // Falla si se agrega o quita un prefijo sin actualizar cleanupAll.
        keys.cleanupAll(stub);

        verify(stub).getStateByRange("model_round_", "model_round_~");
        verify(stub).getStateByRange("model_hash_", "model_hash_~");
        verify(stub).getStateByRange("tally_", "tally_~");
        verify(stub).getStateByRange("update_hashes_", "update_hashes_~");
        verify(stub).getStateByRange("update_", "update_~");
        verify(stub).getStateByRange("nullifier_", "nullifier_~");

        // "update_hashes_" es redundante con "update_", pero se barre aparte
        // para no depender de que ambos prefijos sigan coincidiendo.
        verify(stub, times(6)).getStateByRange(anyString(), anyString());
    }

    @Test
    void initialize_invokesCleanupBeforeWritingNewKeys() {
        mockRangeQuery("nullifier_", "nullifier_~", Map.of(
            "nullifier_3_oldhex", "<oldAnonCidB64>"
        ));

        keys.initialize(stub, "<xas>", "<initialModel>", 5, "fedavg", false, 26, "<modelHash>");

        verify(stub).delState("nullifier_3_oldhex");
        verify(stub).putStringState("as_public_key", "<xas>");
        verify(stub).putStringState("model_round_1", "<initialModel>");
        verify(stub).putStringState("round_status", "1");
        verify(stub).putStringState("total_clients", "5");
        verify(stub).putStringState("aggregation_strategy", "fedavg");
        verify(stub).putStringState("skip_pbs", "false");
        verify(stub).putStringState("num_params", "26");

        // La limpieza va antes de cualquier escritura.
        var inOrder = inOrder(stub);
        inOrder.verify(stub).delState("nullifier_3_oldhex");
        inOrder.verify(stub).putStringState("as_public_key", "<xas>");
    }

    @Test
    void initialize_overwritesStaleNumParams() {
        // Reinicializar con un modelo de otra dimensión sin bajar la red:
        // persiste el segundo num_params.
        keys.initialize(stub, "<xas>", "<m1>", 2, "fedavg", false, 26, "<modelHash1>");
        verify(stub).putStringState("num_params", "26");

        clearInvocations(stub);

        keys.initialize(stub, "<xas>", "<m2>", 2, "fedavg", false, 7850, "<modelHash2>");
        verify(stub).putStringState("num_params", "7850");
        verify(stub, never()).putStringState("num_params", "26");
    }

    /**
     * El iterador es de un solo uso: si el código consume el rango dos veces,
     * hay que volver a llamar este helper.
     */
    @SuppressWarnings("unchecked")
    private void mockRangeQuery(String startKey, String endKey, Map<String, String> entries) {
        QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
        List<KeyValue> kvList = new ArrayList<>();
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>(entries);
        for (var entry : ordered.entrySet()) {
            KeyValue kv = mock(KeyValue.class);
            when(kv.getKey()).thenReturn(entry.getKey());
            when(kv.getStringValue()).thenReturn(entry.getValue());
            kvList.add(kv);
        }
        when(iter.iterator()).thenReturn(kvList.iterator());
        when(stub.getStateByRange(startKey, endKey)).thenReturn(iter);
    }
}
