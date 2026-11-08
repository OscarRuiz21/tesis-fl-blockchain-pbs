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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Auditoría de la ronda en el cliente, tras cada agregación. P4: el
 * {@code H(Δw)} propio está entre los hashes de {@code queryUpdateHashes}.
 * P5: el {@code T_r} recalculado coincide con el del ledger. Además se
 * comprueba {@code H(w_{r+1})} antes de usar el modelo global.
 *
 * <p>Una consulta fallida o una ronda sin agregar da
 * {@link AuditResult.Status#INCONCLUSIVE}. Una discrepancia da
 * {@link AuditResult.Status#FAIL} y aborta solo con
 * {@code fl.audit.strict=true}; como se comparan los mismos bytes que el
 * chaincode hasheó, un FAIL indica manipulación y no ruido numérico.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final BlockchainGateway blockchain;
    private final FileMetricsStore metrics;
    private final boolean enabled;
    private final boolean strict;

    public AuditService(BlockchainGateway blockchain,
                        FileMetricsStore metrics,
                        @Value("${fl.audit.enabled:true}") boolean enabled,
                        @Value("${fl.audit.strict:false}") boolean strict) {
        this.blockchain = blockchain;
        this.metrics = metrics;
        this.enabled = enabled;
        this.strict = strict;
    }

    /** {@code localUpdate} son los mismos bytes de {@code Δw_r^k} que se enviaron. */
    public AuditResult audit(RoundId r, ClientId me, byte[] localUpdate) {
        if (!enabled) {
            return AuditResult.disabled(r);
        }

        List<String> publishedHashes;
        try {
            publishedHashes = blockchain.queryUpdateHashes(r);
        } catch (BlockchainUnavailableException e) {
            log.warn("[client={}] round={} auditoría inconclusa: queryUpdateHashes falló: {}",
                me.value(), r.value(), e.getMessage());
            return inconclusiveRecorded(r, me);
        }
        if (publishedHashes == null || publishedHashes.isEmpty()) {
            log.warn("[client={}] round={} auditoría inconclusa: sin hashes publicados "
                + "(¿ronda no agregada?)", me.value(), r.value());
            return inconclusiveRecorded(r, me);
        }

        // El modelo de la ronda siguiente trae el Tally Hash de r y H(w_{r+1}).
        GlobalModel next;
        try {
            next = blockchain.queryGlobalModel(r.next());
        } catch (BlockchainUnavailableException e) {
            log.warn("[client={}] round={} auditoría inconclusa: queryGlobalModel(r+1) falló: {}",
                me.value(), r.value(), e.getMessage());
            return inconclusiveRecorded(r, me);
        }

        // P4
        String localHashHex = ProtocolHash.sha256Hex(localUpdate);
        boolean p4 = publishedHashes.contains(localHashHex);

        // P5: se reordena localmente en vez de confiar en el orden recibido.
        List<String> sorted = new ArrayList<>(publishedHashes);
        sorted.sort(Comparator.naturalOrder());
        String recomputedTally = ProtocolHash.computeTallyHashHexFromSortedHashes(sorted);
        TallyHash publishedTally = next.previousTally();
        String publishedTallyHex = publishedTally == null ? null : publishedTally.hex();
        boolean p5 = publishedTallyHex != null && recomputedTally.equals(publishedTallyHex);

        String publishedModelHash = next.publishedModelHash();
        boolean modelHashChecked = publishedModelHash != null;
        boolean modelHashMatch = !modelHashChecked || publishedModelHash.equals(next.sha256Hex());

        boolean ok = p4 && p5 && modelHashMatch;
        AuditResult result = new AuditResult(
            r, ok ? AuditResult.Status.PASS : AuditResult.Status.FAIL,
            p4, p5, modelHashChecked, modelHashMatch,
            localHashHex, recomputedTally, publishedTallyHex, publishedHashes.size());

        if (ok) {
            log.info("[client={}] round={} AUDITORÍA OK: P4(inclusión)=OK P5(tally)=OK "
                + "H(w)={} (hashes={})",
                me.value(), r.value(), modelHashChecked ? "OK" : "n/a", publishedHashes.size());
        } else {
            log.error("[client={}] round={} AUDITORÍA FALLÓ: P4={} P5={} H(w)={} | "
                + "localHash={} recomputedTally={} publishedTally={}",
                me.value(), r.value(), p4, p5,
                modelHashChecked ? modelHashMatch : "n/a",
                localHashHex, recomputedTally, publishedTallyHex);
        }

        metrics.recordAudit(r, me, result);

        if (!ok && strict) {
            throw new AuditFailedException(
                "Auditoría de verificabilidad falló en la ronda " + r.value(),
                java.util.Map.of(
                    "round", r.value(),
                    "p4Included", p4,
                    "p5TallyMatch", p5,
                    "modelHashMatch", modelHashMatch));
        }
        return result;
    }

    private AuditResult inconclusiveRecorded(RoundId r, ClientId me) {
        AuditResult result = AuditResult.inconclusive(r);
        metrics.recordAudit(r, me, result);
        return result;
    }
}
