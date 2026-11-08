package org.fl.client.model;

/**
 * Resultado de la auditoría de una ronda en el cliente. {@code p4Included}:
 * {@code H(Δw)} propio aparece en {@code queryUpdateHashes} (P4).
 * {@code p5TallyMatch}: el {@code T_r} recalculado coincide con el del ledger
 * (P5). {@code modelHashMatch}: {@code H(w_{r+1})} coincide con el publicado,
 * cuando el chaincode lo publicó.
 *
 * <p>{@link Status#INCONCLUSIVE} significa que la ronda aún no agregaba o que
 * falló una consulta al ledger; no es una violación de integridad.</p>
 */
public record AuditResult(
    RoundId roundId,
    Status status,
    boolean p4Included,
    boolean p5TallyMatch,
    boolean modelHashChecked,
    boolean modelHashMatch,
    String localUpdateHashHex,
    String recomputedTallyHex,
    String publishedTallyHex,
    int hashCount
) {
    public enum Status { PASS, FAIL, INCONCLUSIVE, DISABLED }

    public static AuditResult disabled(RoundId roundId) {
        return new AuditResult(roundId, Status.DISABLED,
            false, false, false, false, null, null, null, 0);
    }

    public static AuditResult inconclusive(RoundId roundId) {
        return new AuditResult(roundId, Status.INCONCLUSIVE,
            false, false, false, false, null, null, null, 0);
    }

    public boolean passed() {
        return status == Status.PASS;
    }

    public boolean failed() {
        return status == Status.FAIL;
    }
}
