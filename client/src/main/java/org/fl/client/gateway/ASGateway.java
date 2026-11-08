package org.fl.client.gateway;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.bouncycastle.math.ec.ECPoint;
import org.fl.client.crypto.CryptoCodec;
import org.fl.client.crypto.PBSDomainParams;
import org.fl.client.exception.AuthenticationFailedException;
import org.fl.client.model.ClientId;
import org.fl.client.model.Commitment;
import org.fl.client.model.RoundId;
import org.fl.client.model.SessionId;
import org.fl.client.model.SignatureResponse;
import org.fl.client.security.MtlsContextLoader;
import org.fl.client.store.ClientBootstrap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.net.ssl.SSLContext;
import java.math.BigInteger;
import java.nio.file.Path;

/**
 * Cliente mTLS hacia el AS para la Etapa 1, con el certificado
 * {@code User{N}@org2.fl.network}.
 *
 * <p>El {@link RestClient} se construye en el primer uso porque el
 * {@code clientId}, del que depende el certificado, llega después del
 * arranque por {@code POST /lifecycle/start}. Basta {@code volatile} sin
 * {@code synchronized}: cada instancia tiene un solo hilo de experimento.</p>
 */
@Component
public class ASGateway {

    private final ClientBootstrap bootstrap;
    private final PBSDomainParams params;
    private final ASErrorTranslator errorTranslator;
    private final String authServerUrl;

    private volatile RestClient cachedClient;
    /**
     * El AS no rota su par de llaves durante un experimento, así que basta un
     * GET por proceso. Si el AS se redesplegara con otras llaves, habría que
     * reiniciar también los clientes.
     */
    private volatile ECPoint cachedXAS;

    public ASGateway(ClientBootstrap bootstrap,
                     PBSDomainParams params,
                     ASErrorTranslator errorTranslator,
                     @Value("${fl.auth-server-url}") String authServerUrl) {
        this.bootstrap = bootstrap;
        this.params = params;
        this.errorTranslator = errorTranslator;
        this.authServerUrl = authServerUrl;
    }

    private RestClient mtlsClient() {
        RestClient c = cachedClient;
        if (c == null) {
            ClientId me = bootstrap.clientId();
            cachedClient = c = buildMtlsRestClient(me);
        }
        return c;
    }

    private RestClient buildMtlsRestClient(ClientId me) {
        try {
            // El coordinator asigna "client-N", que corresponde a User{N}.
            String idx = me.value().substring("client-".length());
            Path tlsDir = Path.of(
                "/opt/fabric/organizations/peerOrganizations/org2.fl.network/users/User"
                + idx + "@org2.fl.network/tls");
            SSLContext ssl = MtlsContextLoader.buildMtlsContext(
                tlsDir.resolve("client.crt"),
                tlsDir.resolve("client.key"),
                tlsDir.resolve("ca.crt"));

            CloseableHttpClient http = HttpClients.custom()
                .setConnectionManager(
                    PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(new SSLConnectionSocketFactory(ssl))
                        .build())
                .build();
            return RestClient.builder()
                .baseUrl(authServerUrl)
                .requestFactory(new HttpComponentsClientHttpRequestFactory(http))
                .build();
        } catch (Exception e) {
            throw new AuthenticationFailedException(
                "Failed to build mTLS RestClient for " + me.value(), e);
        }
    }

    /**
     * {@code X_AS = x_AS · G}. Solo la primera ronda toca la red; el tiempo se
     * reporta como {@code phase1_fetch_pubkey_rtt_ms}.
     */
    public ECPoint fetchServerPublicKey() {
        ECPoint cached = cachedXAS;
        if (cached != null) {
            return cached;
        }
        try {
            ASDtos.PubKeyResult result = mtlsClient().get()
                .uri("/blind-sign/public-key")
                .retrieve()
                .body(ASDtos.PubKeyResult.class);
            if (result == null || result.publicKeyB64() == null) {
                throw errorTranslator.translatePubKey(
                    new RestClientException("Empty body in /public-key response"));
            }
            ECPoint fetched = CryptoCodec.decodePoint(result.publicKeyB64(), params.curve());
            cachedXAS = fetched;
            return fetched;
        } catch (RestClientException ex) {
            throw errorTranslator.translatePubKey(ex);
        }
    }

    /** El AS abre la sesión y guarda sus secretos efímeros (u, sAux, dAux) bajo sessionId. */
    public CommitmentSession requestCommitment(ClientId clientId, RoundId roundId) {
        try {
            ASDtos.CommitResult result = mtlsClient().post()
                .uri("/blind-sign/commit")
                .body(new ASDtos.CommitReq(clientId.value(), roundId.value()))
                .retrieve()
                .body(ASDtos.CommitResult.class);
            if (result == null) {
                throw errorTranslator.translateCommit(
                    new RestClientException("Empty body in /commit response"),
                    clientId, roundId);
            }
            return new CommitmentSession(
                SessionId.of(result.sessionId()),
                new Commitment(
                    CryptoCodec.decodePoint(result.aB64(), params.curve()),
                    CryptoCodec.decodePoint(result.bB64(), params.curve())));
        } catch (RestClientException ex) {
            throw errorTranslator.translateCommit(ex, clientId, roundId);
        }
    }

    /**
     * Los escalares no se validan contra {@code [1, n-1]} porque el cero es
     * legítimo; la verificación local de la credencial es el filtro final.
     */
    public SignatureResponse requestSignature(SessionId sessionId, BigInteger e) {
        try {
            ASDtos.SignResult result = mtlsClient().post()
                .uri("/blind-sign/sign")
                .body(new ASDtos.SignReq(sessionId.asString(), CryptoCodec.encodeScalar(e)))
                .retrieve()
                .body(ASDtos.SignResult.class);
            if (result == null || result.rAnsB64() == null) {
                throw errorTranslator.translateSign(
                    new RestClientException("Empty body in /sign response"),
                    sessionId);
            }
            BigInteger rAns = decodeRawScalar(result.rAnsB64());
            BigInteger c    = decodeRawScalar(result.cB64());
            BigInteger s    = decodeRawScalar(result.sB64());
            BigInteger d    = decodeRawScalar(result.dB64());
            return new SignatureResponse(rAns, c, s, d);
        } catch (RestClientException ex) {
            throw errorTranslator.translateSign(ex, sessionId);
        }
    }

    private static BigInteger decodeRawScalar(String b64) {
        return new BigInteger(1, java.util.Base64.getDecoder().decode(b64));
    }

    public record CommitmentSession(SessionId sessionId, Commitment commitment) {}
}
