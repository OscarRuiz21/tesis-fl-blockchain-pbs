package org.fl.client.gateway;

import org.fl.client.exception.TrainingFailedException;
import org.fl.client.model.ClientId;
import org.fl.client.model.GlobalModel;
import org.fl.client.model.RoundId;
import org.fl.client.model.TrainingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;

class PythonTrainingGatewayTest {

    private ProcessSupervisor supervisor;
    private IPCProtocol ipc;
    private StdoutMetricsParser metricsParser;
    private PythonTrainingGateway gateway;

    private GlobalModel base;
    private ClientId me;

    @BeforeEach
    void setUp() {
        supervisor = mock(ProcessSupervisor.class);
        ipc = mock(IPCProtocol.class);
        metricsParser = new StdoutMetricsParser();

        gateway = new PythonTrainingGateway(
            supervisor, ipc, metricsParser,
            "/opt/venv/bin/python",
            "/opt/python/train.py",
            1L);

        byte[] modelBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.1f).putFloat(0.2f).array();
        base = GlobalModel.of(modelBytes, RoundId.of(1), null);
        me = ClientId.of("client-1");
    }

    @Test
    void buildsCorrectCommandLine() throws Exception {
        byte[] updateBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.01f).putFloat(0.02f).array();
        when(ipc.readBinary(any())).thenReturn(updateBytes);

        ProcessSupervisor.ProcessResult ok = new ProcessSupervisor.ProcessResult(
            0, "accuracy=0.9 loss=0.1 num_samples=42", "", 50L);
        when(supervisor.run(any(), any(), any())).thenReturn(ok);

        gateway.train("logistic_regression", 2, base, Path.of("/data"),
            me, Map.of("epochs", 3, "lr", 0.01));

        @SuppressWarnings("unchecked")
        var cmdCaptor = forClass(List.class);
        @SuppressWarnings("unchecked")
        var envCaptor = forClass(Map.class);
        verify(supervisor).run(any(), cmdCaptor.capture(), envCaptor.capture());

        @SuppressWarnings("unchecked")
        List<String> cmd = cmdCaptor.getValue();
        assertThat(cmd).containsSequence(
            "/opt/venv/bin/python", "/opt/python/train.py",
            "--model_name", "logistic_regression",
            "--num_params", "2");
        assertThat(cmd).contains("--client_id", "client-1");
        assertThat(cmd).contains("--data", "/data");

        int idx = cmd.indexOf("--hyperparams");
        assertThat(idx).isGreaterThan(-1);
        assertThat(cmd.get(idx + 1)).contains("\"epochs\":3").contains("\"lr\":0.01");

        // PYTHONPATH es el abuelo del script.
        @SuppressWarnings("unchecked")
        Map<String, String> env = envCaptor.getValue();
        assertThat(env.get("PYTHONPATH")).isEqualTo("/opt");
    }

    @Test
    void nonZeroExitTriggersTrainingFailedWithStderr() throws Exception {
        ProcessSupervisor.ProcessResult fail = new ProcessSupervisor.ProcessResult(
            2, "", "Traceback (most recent call last)\nValueError: bad config", 30L);
        when(supervisor.run(any(), any(), any())).thenReturn(fail);

        assertThatThrownBy(() -> gateway.train("logistic_regression", 2, base,
            Path.of("/data"), me, Map.of()))
            .isInstanceOf(TrainingFailedException.class)
            .hasMessageContaining("exit code 2")
            .satisfies(ex -> assertThat(((TrainingFailedException) ex).getStderr())
                .contains("ValueError"));
    }

    @Test
    void parsesStdoutCorrectly() throws Exception {
        byte[] updateBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(0.03f).putFloat(0.04f).array();
        when(ipc.readBinary(any())).thenReturn(updateBytes);

        ProcessSupervisor.ProcessResult ok = new ProcessSupervisor.ProcessResult(
            0,
            "Epoch 1/3 done\naccuracy=0.7345 loss=0.4321 num_samples=512",
            "",
            120L);
        when(supervisor.run(any(), any(), any())).thenReturn(ok);

        TrainingResult result = gateway.train("logistic_regression", 2, base,
            Path.of("/data"), me, Map.of());

        assertThat(result.trainingAccuracy()).isEqualTo(0.7345);
        assertThat(result.trainingLoss()).isEqualTo(0.4321);
        assertThat(result.numSamples()).isEqualTo(512);
        assertThat(result.duration()).isEqualTo(Duration.ofMillis(120));
    }

    @Test
    void ioErrorOnSubprocessSurfacesAsTrainingFailed() throws Exception {
        when(supervisor.run(any(), any(), any())).thenThrow(new IOException("pipe closed"));

        assertThatThrownBy(() -> gateway.train("logistic_regression", 2, base,
            Path.of("/data"), me, Map.of()))
            .isInstanceOf(TrainingFailedException.class)
            .hasMessageContaining("exit code -1");
    }
}
