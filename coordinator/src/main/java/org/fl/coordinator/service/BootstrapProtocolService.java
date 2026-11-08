package org.fl.coordinator.service;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.math.ec.ECPoint;
import org.fl.coordinator.crypto.ModelSerializer;
import org.fl.coordinator.gateway.ASGateway;
import org.fl.coordinator.gateway.BlockchainGateway;
import org.fl.coordinator.model.ClientId;
import org.fl.coordinator.model.ExperimentSpec;
import org.fl.coordinator.model.GlobalModel;
import org.fl.coordinator.model.RoundId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Preparación previa a la primera ronda: registra en el contrato la llave
 * pública del AS y el modelo inicial en ceros, y carga en el AS la whitelist
 * {@code client-1..client-K}.
 */
@Service
@RequiredArgsConstructor
public class BootstrapProtocolService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapProtocolService.class);

    private final ASGateway asGateway;
    private final BlockchainGateway blockchainGateway;

    public void bootstrap(ExperimentSpec spec) {
        log.info("Bootstrapping experiment '{}' (K={}, rounds={}, strategy={}, mode={}, numParams={})",
            spec.label(), spec.k(), spec.totalRounds(),
            spec.aggregationStrategy(), spec.anonymityMode(), spec.numParams());

        GlobalModel initialModel = buildInitialModel(spec.numParams());

        ECPoint xAS = asGateway.fetchServerPublicKey();
        log.info("AS public key fetched");

        blockchainGateway.initProtocol(
            xAS,
            initialModel,
            spec.k(),
            spec.aggregationStrategy(),
            spec.anonymityMode(),
            spec.numParams());

        List<ClientId> clients = IntStream.rangeClosed(1, spec.k())
            .mapToObj(i -> ClientId.of("client-" + i))
            .toList();
        asGateway.initWhitelist(clients, spec.totalRounds(), spec.label());
        log.info("AS whitelist: {} clients registered for {} rounds", spec.k(), spec.totalRounds());
    }

    // Ceros y no pesos aleatorios: inicializarlos al azar requeriría llamar a Python.
    private static GlobalModel buildInitialModel(int numParams) {
        float[] zeros = new float[numParams];
        byte[] bytes = ModelSerializer.serializeFloats(zeros);
        return GlobalModel.of(bytes, RoundId.of(1), null);
    }
}
