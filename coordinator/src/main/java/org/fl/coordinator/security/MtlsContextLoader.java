package org.fl.coordinator.security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * {@link SSLContext} mTLS a partir de los PEM de {@code scripts/gen-certs.sh}.
 * El AS valida la cadena y exige CN=coordinator para {@code POST /admin/init}.
 */
public final class MtlsContextLoader {

    private MtlsContextLoader() {
        throw new AssertionError("utility class");
    }

    public static SSLContext buildMtlsContext(
        Path clientCertPath, Path clientKeyPath, Path caCertPath
    ) throws IOException, GeneralSecurityException {

        char[] inMemoryPassword = "changeit".toCharArray();
        KeyStore keyStore = loadClientKeyStore(clientCertPath, clientKeyPath, inMemoryPassword);
        KeyStore trustStore = loadTrustStore(caCertPath);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(keyStore, inMemoryPassword);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return ctx;
    }

    private static KeyStore loadClientKeyStore(
        Path certPath, Path keyPath, char[] inMemoryPassword
    ) throws IOException, GeneralSecurityException {
        X509Certificate cert = parseCertificate(certPath);
        PrivateKey privateKey = parsePrivateKey(keyPath);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("client", privateKey, inMemoryPassword,
            new java.security.cert.Certificate[]{cert});
        return ks;
    }

    private static KeyStore loadTrustStore(Path caCertPath)
            throws IOException, GeneralSecurityException {
        X509Certificate caCert = parseCertificate(caCertPath);
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("ca", caCert);
        return ts;
    }

    private static X509Certificate parseCertificate(Path pemPath)
            throws IOException, GeneralSecurityException {
        byte[] bytes = Files.readAllBytes(pemPath);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(bytes));
    }

    private static PrivateKey parsePrivateKey(Path pemPath)
            throws IOException, GeneralSecurityException {
        String pem = Files.readString(pemPath);
        // El patrón quita cualquier cabecera PEM de llave privada, pero el
        // contenido debe ser PKCS8; gen-certs.sh convierte la llave SEC1.
        String base64 = pem
            .replaceAll("-----BEGIN [A-Z ]*PRIVATE KEY-----", "")
            .replaceAll("-----END [A-Z ]*PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("EC").generatePrivate(spec);
    }
}
