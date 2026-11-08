package org.fl.as.store;

import org.fl.as.model.ClientId;
import org.fl.as.model.RoundId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whitelist del experimento y ronda activa, en memoria.
 *
 * <p>El AS avanza la ronda activa por su cuenta, de forma monótona, a partir
 * de las peticiones de credencial (un cliente solo pide la ronda r tras ver
 * que el ledger avanzó a r); el coordinator no interviene en la lógica de
 * rondas. Con ella se rechazan credenciales para rondas ya concluidas. Una
 * ronda futura ({@code roundId > activeRound}) no se rechaza aquí a propósito:
 * esa credencial no puede reutilizarse porque queda anclada a {@code Z_r} en el
 * contrato y el {@link IssuanceLedger} solo admite una por (cliente, ronda).
 * Bajo el modelo semi-honesto los clientes no mienten sobre la ronda.</p>
 */
@Component
public class EligibilityRegistry {

    private final Set<String> authorized = ConcurrentHashMap.newKeySet();
    private volatile int totalRounds = 0;
    private volatile long activeRound = 1;

    public void registerAll(List<ClientId> clientIds, int totalRounds) {
        authorized.clear();
        clientIds.forEach(id -> authorized.add(id.value()));
        this.totalRounds = totalRounds;
        this.activeRound = 1;
    }

    public boolean isEligible(ClientId clientId, RoundId roundId) {
        return authorized.contains(clientId.value())
            && roundId.value() >= 1
            && roundId.value() <= totalRounds;
    }

    public long activeRound() {
        return activeRound;
    }

    /**
     * Nunca retrocede. Avanzar tarde solo aplaza el rechazo de rondas
     * concluidas; nunca rechaza una petición honesta en curso.
     */
    public void advanceActiveRound(long round) {
        if (round > activeRound) {
            activeRound = round;
        }
    }

    public boolean isRoundConcluded(RoundId roundId) {
        return roundId.value() < activeRound;
    }

    public int count() {
        return authorized.size();
    }
}
