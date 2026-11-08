package org.fl.as.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.fl.as.controller.AuthDtos.AdminInitRequest;
import org.fl.as.service.RegisterEligibilityService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({GlobalExceptionHandler.class})
@TestPropertySource(properties = "auth.admin-cn=coordinator")
class AdminControllerTest {

    private static final String CERT_ATTR = "jakarta.servlet.request.X509Certificate";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @MockBean private RegisterEligibilityService registerEligibility;

    private static X509Certificate certCoordinator;
    private static X509Certificate certClient1;

    @BeforeAll
    static void generateTestCerts() throws Exception {
        certCoordinator = generateSelfSignedCert("CN=coordinator,O=org2.fl.network");
        certClient1     = generateSelfSignedCert("CN=User1@org2.fl.network,O=org2.fl.network");
    }

    private static MockHttpServletRequestBuilder withCert(MockHttpServletRequestBuilder b,
                                                          X509Certificate cert) {
        return b.requestAttr(CERT_ATTR, new X509Certificate[]{cert});
    }

    @Test
    void postInitWithoutCertReturns401() throws Exception {
        mvc.perform(post("/admin/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new AdminInitRequest(List.of("client-1", "client-2"), 5, "exp-001"))))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("client_not_authenticated"));
    }

    @Test
    void postInitWithNonAdminCnReturns403() throws Exception {
        // Un certificado de cliente válido no basta para /admin.
        mvc.perform(withCert(post("/admin/init"), certClient1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new AdminInitRequest(List.of("client-1", "client-2"), 5, "exp-001"))))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("admin_access_denied"));
    }

    @Test
    void postInitWithAdminCnAndValidBodyReturns200AndDelegates() throws Exception {
        mvc.perform(withCert(post("/admin/init"), certCoordinator)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new AdminInitRequest(List.of("client-1", "client-2"), 5, "exp-001"))))
           .andExpect(status().isOk());

        verify(registerEligibility, times(1)).register(any(), anyInt());
    }

    @Test
    void postInitWithEmptyAllowedClientsReturns400() throws Exception {
        mvc.perform(withCert(post("/admin/init"), certCoordinator)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new AdminInitRequest(List.of(), 5, "exp-001"))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("validation_error"));
    }

    private static X509Certificate generateSelfSignedCert(String subjectDn) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        X500Name subject = new X500Name(subjectDn);
        Date notBefore = new Date(System.currentTimeMillis() - 60_000);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject,
            SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded()));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
