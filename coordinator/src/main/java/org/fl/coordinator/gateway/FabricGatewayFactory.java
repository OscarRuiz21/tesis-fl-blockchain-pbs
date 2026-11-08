package org.fl.coordinator.gateway;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;

/** Conexión a Fabric Gateway (gRPC con TLS e identidad X.509), igual a la del fl-client. */
public final class FabricGatewayFactory {

    private FabricGatewayFactory() {}

    public static GatewayHandle connect(FabricConnectionParams p) throws IOException {
        Identity identity = loadIdentity(p);
        Signer signer = loadSigner(p);
        ManagedChannel channel = buildChannel(p);

        Gateway gateway = Gateway.newInstance()
            .identity(identity)
            .signer(signer)
            .connection(channel)
            .connect();

        Contract contract = gateway.getNetwork(p.channel()).getContract(p.chaincode());
        return new GatewayHandle(gateway, contract, channel);
    }

    private static Identity loadIdentity(FabricConnectionParams p) throws IOException {
        Path certPath = findFirstFile(p.signCertPath());
        try (var reader = Files.newBufferedReader(certPath)) {
            X509Certificate cert = Identities.readX509Certificate(reader);
            return new X509Identity(p.mspId(), cert);
        } catch (Exception e) {
            throw new IOException("Failed to load identity from " + certPath, e);
        }
    }

    private static Signer loadSigner(FabricConnectionParams p) throws IOException {
        Path keyPath = findFirstFile(p.keystorePath());
        try (var reader = Files.newBufferedReader(keyPath)) {
            PrivateKey key = Identities.readPrivateKey(reader);
            return Signers.newPrivateKeySigner(key);
        } catch (Exception e) {
            throw new IOException("Failed to load signing key from " + keyPath, e);
        }
    }

    private static ManagedChannel buildChannel(FabricConnectionParams p) throws IOException {
        try {
            var sslContext = GrpcSslContexts.forClient()
                .trustManager(p.tlsCaCertPath().toFile())
                .build();
            return NettyChannelBuilder.forTarget(p.peerEndpoint())
                .sslContext(sslContext)
                .overrideAuthority(p.peerOverrideAuthority())
                .build();
        } catch (Exception e) {
            throw new IOException("Failed to build gRPC channel", e);
        }
    }

    private static Path findFirstFile(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.findFirst()
                .orElseThrow(() -> new IOException("No file in " + dir));
        }
    }

    public record FabricConnectionParams(
        String channel, String chaincode, String mspId,
        String peerEndpoint, String peerOverrideAuthority,
        Path signCertPath, Path keystorePath, Path tlsCaCertPath
    ) {}

    public record GatewayHandle(Gateway gateway, Contract contract, ManagedChannel channel) {
        public void close() {
            gateway.close();
            channel.shutdownNow();
        }
    }
}
