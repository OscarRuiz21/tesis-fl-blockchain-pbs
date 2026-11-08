package org.fl.as.service;

import lombok.RequiredArgsConstructor;
import org.fl.as.model.ClientId;
import org.fl.as.store.EligibilityRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

/** Reemplaza la whitelist y el total de rondas; se invoca antes de la ronda 1. */
@Service
@RequiredArgsConstructor
public class RegisterEligibilityService {

    private final EligibilityRegistry registry;

    public void register(List<ClientId> clientIds, int totalRounds) {
        if (clientIds == null || clientIds.isEmpty()) {
            throw new IllegalArgumentException("clientIds must be non-empty");
        }
        if (totalRounds < 1) {
            throw new IllegalArgumentException(
                "totalRounds must be >= 1, got " + totalRounds);
        }
        registry.registerAll(clientIds, totalRounds);
    }

    public int countAuthorized() {
        return registry.count();
    }
}
