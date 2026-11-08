package org.fl.as.security;

import jakarta.servlet.http.HttpServletRequest;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.fl.as.exception.ClientNotAuthenticatedException;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/** Extrae la identidad del cliente del certificado presentado en el handshake mTLS. */
public final class CertSubjectExtractor {

    private static final String CERT_ATTR = "jakarta.servlet.request.X509Certificate";

    private CertSubjectExtractor() {
        throw new AssertionError("utility class");
    }

    public static X509Certificate requireClientCert(HttpServletRequest http) {
        X509Certificate[] certs = (X509Certificate[]) http.getAttribute(CERT_ATTR);
        if (certs == null || certs.length == 0) {
            throw new ClientNotAuthenticatedException(
                "no client certificate present in TLS handshake");
        }
        return certs[0];
    }

    /**
     * Se usa el parser de Bouncy Castle y no una expresión regular porque los
     * DN pueden traer varios RDN y en orden no estándar.
     */
    public static String extractCommonName(X509Certificate cert) {
        try {
            X500Name name = new JcaX509CertificateHolder(cert).getSubject();
            RDN[] cnRdns = name.getRDNs(BCStyle.CN);
            if (cnRdns.length == 0) {
                throw new ClientNotAuthenticatedException(
                    "cert subject has no CN: " + cert.getSubjectX500Principal().getName());
            }
            AttributeTypeAndValue first = cnRdns[0].getFirst();
            return IETFUtils.valueToString(first.getValue());
        } catch (CertificateEncodingException e) {
            throw new ClientNotAuthenticatedException(
                "cannot parse cert subject: " + e.getMessage());
        }
    }
}
