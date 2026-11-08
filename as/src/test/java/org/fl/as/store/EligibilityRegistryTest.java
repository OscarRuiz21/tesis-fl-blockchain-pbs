package org.fl.as.store;

import org.fl.as.model.ClientId;
import org.fl.as.model.RoundId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityRegistryTest {

    private EligibilityRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EligibilityRegistry();
    }

    @Test
    void registerAllStoresClients() {
        registry.registerAll(List.of(ClientId.of("c1"), ClientId.of("c2")), 5);

        assertThat(registry.count()).isEqualTo(2);
        assertThat(registry.isEligible(ClientId.of("c1"), RoundId.of(3))).isTrue();
    }

    @Test
    void isEligibleTrueForRegistered() {
        registry.registerAll(List.of(ClientId.of("c1")), 10);

        assertThat(registry.isEligible(ClientId.of("c1"), RoundId.of(1))).isTrue();
        assertThat(registry.isEligible(ClientId.of("c1"), RoundId.of(10))).isTrue();
    }

    @Test
    void isEligibleFalseForRoundOutOfBounds() {
        registry.registerAll(List.of(ClientId.of("c1")), 5);

        assertThat(registry.isEligible(ClientId.of("c1"), RoundId.of(6))).isFalse();
        assertThat(registry.isEligible(ClientId.of("c-not-registered"), RoundId.of(1))).isFalse();
    }

    @Test
    void activeRoundStartsAtOneAndAdvancesMonotonically() {
        registry.registerAll(List.of(ClientId.of("c1")), 10);
        assertThat(registry.activeRound()).isEqualTo(1L);

        registry.advanceActiveRound(3);
        assertThat(registry.activeRound()).isEqualTo(3L);

        // No retrocede ante un valor menor o igual.
        registry.advanceActiveRound(2);
        assertThat(registry.activeRound()).isEqualTo(3L);
    }

    @Test
    void isRoundConcludedForRoundsBelowActive() {
        registry.registerAll(List.of(ClientId.of("c1")), 10);
        registry.advanceActiveRound(4);

        assertThat(registry.isRoundConcluded(RoundId.of(3))).isTrue();   // terminada
        assertThat(registry.isRoundConcluded(RoundId.of(4))).isFalse();  // activa
        assertThat(registry.isRoundConcluded(RoundId.of(5))).isFalse();  // futura (aquí no se bloquea)
    }

    @Test
    void registerAllResetsActiveRoundToOne() {
        registry.registerAll(List.of(ClientId.of("c1")), 10);
        registry.advanceActiveRound(7);
        assertThat(registry.activeRound()).isEqualTo(7L);

        // Un nuevo experimento reinicia la ronda activa a 1.
        registry.registerAll(List.of(ClientId.of("c2")), 10);
        assertThat(registry.activeRound()).isEqualTo(1L);
    }
}
