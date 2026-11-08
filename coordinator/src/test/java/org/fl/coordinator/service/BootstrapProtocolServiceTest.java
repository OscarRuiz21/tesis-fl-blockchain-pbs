package org.fl.coordinator.service;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.coordinator.crypto.PBSDomainParams;
import org.fl.coordinator.gateway.ASGateway;
import org.fl.coordinator.gateway.BlockchainGateway;
import org.fl.coordinator.model.ClientId;
import org.fl.coordinator.model.ExperimentSpec;
import org.fl.coordinator.model.GlobalModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapProtocolServiceTest {

    private ASGateway asGateway;
    private BlockchainGateway blockchainGateway;
    private BootstrapProtocolService service;

    @BeforeEach
    void setUp() {
        asGateway = mock(ASGateway.class);
        blockchainGateway = mock(BlockchainGateway.class);
        service = new BootstrapProtocolService(asGateway, blockchainGateway);

        // X_AS dummy pero válido (G · 7).
        ECPoint xAS = PBSDomainParams.p256().G().multiply(BigInteger.valueOf(7)).normalize();
        when(asGateway.fetchServerPublicKey()).thenReturn(xAS);
    }

    @Test
    void buildsInitialModelAndCallsInitProtocolWithNumParams() {
        ExperimentSpec spec = sampleSpec(2, 5, 26, "fedavg", "pbs");

        service.bootstrap(spec);

        ArgumentCaptor<GlobalModel> modelCap = ArgumentCaptor.forClass(GlobalModel.class);
        verify(blockchainGateway, times(1)).initProtocol(
            any(ECPoint.class),
            modelCap.capture(),
            eq(2),
            eq("fedavg"),
            eq("pbs"),
            eq(26));
        // El modelo inicial debe tener exactamente numParams * 4 bytes (float32 BE).
        assertThat(modelCap.getValue().bytes()).hasSize(26 * 4);
    }

    @Test
    void registersBaseOneClientsInAS() {
        ExperimentSpec spec = sampleSpec(3, 7, 26, "fedmedian", "none");

        service.bootstrap(spec);

        ArgumentCaptor<List<ClientId>> capt = ArgumentCaptor.forClass(List.class);
        verify(asGateway).initWhitelist(capt.capture(), eq(7), eq("test-exp"));

        List<ClientId> clients = capt.getValue();
        assertThat(clients).hasSize(3);
        assertThat(clients.get(0).value()).isEqualTo("client-1");
        assertThat(clients.get(2).value()).isEqualTo("client-3");
    }

    @Test
    void propagatesExceptionsFromBlockchain() {
        ExperimentSpec spec = sampleSpec(2, 5, 26, "fedavg", "pbs");
        doThrow(new RuntimeException("chaincode unavailable"))
            .when(blockchainGateway).initProtocol(any(), any(), anyInt(), any(), any(), anyInt());

        assertThatThrownBy(() -> service.bootstrap(spec))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("chaincode unavailable");
    }

    private static ExperimentSpec sampleSpec(int k, int totalRounds, int numParams,
                                             String aggregation, String anonymity) {
        return new ExperimentSpec(
            "test-exp", k, totalRounds, 42,
            aggregation, anonymity,
            "logistic_regression", numParams,
            "tabular_csv", Path.of("/tmp/data"), Path.of("/tmp/test.csv"),
            "iid", null,
            Map.of("epochs", 5),
            Duration.ofSeconds(60),
            Duration.ofSeconds(180),
            Duration.ofSeconds(3600),
            Path.of("/tmp/results"));
    }
}
