package org.fl.client.service;

import org.fl.client.crypto.ProtocolHash;
import org.fl.client.exception.AuditFailedException;
import org.fl.client.exception.BlockchainUnavailableException;
import org.fl.client.gateway.BlockchainGateway;
import org.fl.client.model.AuditResult;
import org.fl.client.model.ClientId;
import org.fl.client.model.GlobalModel;
import org.fl.client.model.RoundId;
import org.fl.client.model.TallyHash;
import org.fl.client.store.FileMetricsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private BlockchainGateway blockchain;
    private FileMetricsStore metrics;
    private final ClientId me = ClientId.of("client-1");
    private final RoundId r = RoundId.of(1);

    // Δw local del cliente y el modelo agregado de la ronda siguiente.
    private final byte[] localUpdate = {1, 2, 3, 4};
    private final byte[] otherUpdate = {5, 6, 7, 8};
    private final byte[] nextModelBytes = {9, 10, 11, 12};

    @BeforeEach
    void setUp() {
        blockchain = mock(BlockchainGateway.class);
        metrics = mock(FileMetricsStore.class);
    }

    /** Hashes publicados en orden arbitrario, con Tally Hash y modelo consistentes. */
    private void stubConsistentLedger() {
        String hLocal = ProtocolHash.sha256Hex(localUpdate);
        String hOther = ProtocolHash.sha256Hex(otherUpdate);
        // Publicados desordenados a propósito: el auditor los reordena.
        List<String> published = new ArrayList<>(List.of(hLocal, hOther));
        published.sort(Comparator.reverseOrder());
        when(blockchain.queryUpdateHashes(r)).thenReturn(published);

        List<String> sorted = new ArrayList<>(published);
        sorted.sort(Comparator.naturalOrder());
        String tally = ProtocolHash.computeTallyHashHexFromSortedHashes(sorted);
        String modelHash = ProtocolHash.sha256Hex(nextModelBytes);

        GlobalModel next = GlobalModel.of(
            nextModelBytes, RoundId.of(2), TallyHash.of(tally), modelHash);
        when(blockchain.queryGlobalModel(RoundId.of(2))).thenReturn(next);
    }

    @Test
    void audit_allConsistent_passes() {
        stubConsistentLedger();
        AuditService svc = new AuditService(blockchain, metrics, true, false);

        AuditResult result = svc.audit(r, me, localUpdate);

        assertThat(result.passed()).isTrue();
        assertThat(result.p4Included()).isTrue();
        assertThat(result.p5TallyMatch()).isTrue();
        assertThat(result.modelHashChecked()).isTrue();
        assertThat(result.modelHashMatch()).isTrue();
        verify(metrics).recordAudit(eq(r), eq(me), any(AuditResult.class));
    }

    @Test
    void audit_ownUpdateNotIncluded_failsP4() {
        stubConsistentLedger();
        AuditService svc = new AuditService(blockchain, metrics, true, false);

        // El cliente audita con un Δw que NO fue el que envió (no está en la lista).
        AuditResult result = svc.audit(r, me, new byte[]{99, 99, 99, 99});

        assertThat(result.failed()).isTrue();
        assertThat(result.p4Included()).isFalse();
        // P5 (tally) y hash de modelo siguen siendo correctos.
        assertThat(result.p5TallyMatch()).isTrue();
        verify(metrics).recordAudit(eq(r), eq(me), any(AuditResult.class));
    }

    @Test
    void audit_tamperedTally_failsP5() {
        String hLocal = ProtocolHash.sha256Hex(localUpdate);
        String hOther = ProtocolHash.sha256Hex(otherUpdate);
        when(blockchain.queryUpdateHashes(r)).thenReturn(List.of(hLocal, hOther));
        // Tally publicado adulterado (un hash válido pero distinto al recomputado).
        String wrongTally = ProtocolHash.sha256Hex(new byte[]{0});
        GlobalModel next = GlobalModel.of(
            nextModelBytes, RoundId.of(2), TallyHash.of(wrongTally),
            ProtocolHash.sha256Hex(nextModelBytes));
        when(blockchain.queryGlobalModel(RoundId.of(2))).thenReturn(next);

        AuditService svc = new AuditService(blockchain, metrics, true, false);
        AuditResult result = svc.audit(r, me, localUpdate);

        assertThat(result.failed()).isTrue();
        assertThat(result.p4Included()).isTrue();
        assertThat(result.p5TallyMatch()).isFalse();
    }

    @Test
    void audit_strictMode_throwsOnFailure() {
        stubConsistentLedger();
        AuditService svc = new AuditService(blockchain, metrics, true, true);

        assertThatThrownBy(() -> svc.audit(r, me, new byte[]{99, 99, 99, 99}))
            .isInstanceOf(AuditFailedException.class);
    }

    @Test
    void audit_roundNotAggregated_inconclusive() {
        when(blockchain.queryUpdateHashes(r)).thenReturn(List.of());
        AuditService svc = new AuditService(blockchain, metrics, true, false);

        AuditResult result = svc.audit(r, me, localUpdate);

        assertThat(result.status()).isEqualTo(AuditResult.Status.INCONCLUSIVE);
        // No debe intentar leer el modelo siguiente si no hay hashes.
        verify(blockchain, never()).queryGlobalModel(any());
    }

    @Test
    void audit_queryFails_inconclusive() {
        when(blockchain.queryUpdateHashes(r))
            .thenThrow(new BlockchainUnavailableException(new RuntimeException("down")));
        AuditService svc = new AuditService(blockchain, metrics, true, false);

        AuditResult result = svc.audit(r, me, localUpdate);

        assertThat(result.status()).isEqualTo(AuditResult.Status.INCONCLUSIVE);
    }

    @Test
    void audit_disabled_noLedgerAccess() {
        AuditService svc = new AuditService(blockchain, metrics, false, false);

        AuditResult result = svc.audit(r, me, localUpdate);

        assertThat(result.status()).isEqualTo(AuditResult.Status.DISABLED);
        verifyNoInteractions(blockchain);
    }
}
