package org.fl.as.service;

import org.fl.as.model.ClientId;
import org.fl.as.store.EligibilityRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterEligibilityServiceTest {

    @Test
    void shouldRegisterAllClients() {
        EligibilityRegistry registry = new EligibilityRegistry();
        RegisterEligibilityService service = new RegisterEligibilityService(registry);

        service.register(List.of(ClientId.of("client-1"), ClientId.of("client-2")), 10);

        assertThat(service.countAuthorized()).isEqualTo(2);
    }

    @Test
    void shouldRejectEmptyList() {
        RegisterEligibilityService service = new RegisterEligibilityService(new EligibilityRegistry());

        assertThatThrownBy(() -> service.register(List.of(), 10))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register(null, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveTotalRounds() {
        RegisterEligibilityService service = new RegisterEligibilityService(new EligibilityRegistry());

        assertThatThrownBy(() -> service.register(List.of(ClientId.of("c1")), 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register(List.of(ClientId.of("c1")), -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
