package org.fl.as.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.fl.as.controller.AuthDtos.CommitRequest;
import org.fl.as.controller.AuthDtos.SignRequest;
import org.fl.as.exception.ClientNotEligibleException;
import org.fl.as.exception.DuplicateCredentialRequestException;
import org.fl.as.exception.SessionNotFoundException;
import org.fl.as.model.ClientId;
import org.fl.as.model.CommitmentIssued;
import org.fl.as.model.RoundId;
import org.fl.as.model.SessionId;
import org.fl.as.model.SignatureIssued;
import org.fl.as.service.IssueCommitmentService;
import org.fl.as.service.IssueSignatureService;
import org.fl.as.service.QueryPublicKeyService;
import org.fl.as.service.VerifyClientIdentityService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los services PBS van mockeados, pero {@link VerifyClientIdentityService} es
 * el real para que la comparación de CN se ejerza completa. Los certificados
 * se generan autofirmados y se inyectan con {@code requestAttr(...)}.
 */
@WebMvcTest(BlindSignController.class)
@Import({GlobalExceptionHandler.class, VerifyClientIdentityService.class})
class BlindSignControllerTest {

    private static final String CERT_ATTR = "jakarta.servlet.request.X509Certificate";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @MockBean private IssueCommitmentService issueCommitment;
    @MockBean private IssueSignatureService issueSignature;
    @MockBean private QueryPublicKeyService queryPublicKey;

    private static X509Certificate certUser1;
    private static X509Certificate certUser9;

    @BeforeAll
    static void generateTestCerts() throws Exception {
        certUser1 = generateSelfSignedCert("CN=User1@org2.fl.network,O=org2.fl.network");
        certUser9 = generateSelfSignedCert("CN=User9@org2.fl.network,O=org2.fl.network");
    }

    private static MockHttpServletRequestBuilder withCert(MockHttpServletRequestBuilder b,
                                                          X509Certificate cert) {
        return b.requestAttr(CERT_ATTR, new X509Certificate[]{cert});
    }

    // /commit: coincidencia de CN

    @Test
    void postCommitWithMatchingCertReturns200() throws Exception {
        SessionId sid = SessionId.random();
        when(issueCommitment.issue(any(ClientId.class), any(RoundId.class)))
            .thenReturn(new CommitmentIssued(sid, "A-base64", "B-base64"));

        mvc.perform(withCert(post("/blind-sign/commit"), certUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CommitRequest("client-1", 1L))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sessionId").value(sid.asString()))
           .andExpect(jsonPath("$.aB64").value("A-base64"))
           .andExpect(jsonPath("$.bB64").value("B-base64"));
    }

    @Test
    void postCommitWithoutCertReturns401() throws Exception {
        mvc.perform(post("/blind-sign/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CommitRequest("client-1", 1L))))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("client_not_authenticated"));
    }

    @Test
    void postCommitWithMismatchedCnReturns401() throws Exception {
        mvc.perform(withCert(post("/blind-sign/commit"), certUser9)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CommitRequest("client-1", 1L))))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("client_id_mismatch"));
    }

    @Test
    void postCommitWithUnauthorizedClientReturns401() throws Exception {
        when(issueCommitment.issue(any(), any()))
            .thenThrow(new ClientNotEligibleException(ClientId.of("client-1")));

        mvc.perform(withCert(post("/blind-sign/commit"), certUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CommitRequest("client-1", 1L))))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("client_not_eligible"));
    }

    @Test
    void postCommitWithDuplicateRequestReturns409() throws Exception {
        when(issueCommitment.issue(any(), any()))
            .thenThrow(new DuplicateCredentialRequestException(
                ClientId.of("client-1"), RoundId.of(1)));

        mvc.perform(withCert(post("/blind-sign/commit"), certUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CommitRequest("client-1", 1L))))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.code").value("duplicate_request"));
    }

    // /sign: basta el certificado, sin comparar CN

    @Test
    void postSignWithCertReturns200() throws Exception {
        when(issueSignature.sign(any(SessionId.class), any(String.class)))
            .thenReturn(new SignatureIssued(
                "rAns-base64", "c-base64", "s-base64", "d-base64"));

        mvc.perform(withCert(post("/blind-sign/sign"), certUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new SignRequest("11111111-1111-1111-1111-111111111111", "e-b64"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.rAnsB64").value("rAns-base64"))
           .andExpect(jsonPath("$.cB64").value("c-base64"))
           .andExpect(jsonPath("$.sB64").value("s-base64"))
           .andExpect(jsonPath("$.dB64").value("d-base64"));
    }

    @Test
    void postSignWithoutCertReturns401() throws Exception {
        mvc.perform(post("/blind-sign/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new SignRequest("11111111-1111-1111-1111-111111111111", "e-b64"))))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("client_not_authenticated"));
    }

    @Test
    void postSignWithExpiredSessionReturns404() throws Exception {
        SessionId sid = SessionId.random();
        when(issueSignature.sign(any(), any()))
            .thenThrow(new SessionNotFoundException(sid));

        mvc.perform(withCert(post("/blind-sign/sign"), certUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new SignRequest(sid.asString(), "e-b64"))))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("session_not_found"));
    }

    // /public-key: sin certificado

    @Test
    void getPublicKeyReturns200AndPubKey() throws Exception {
        when(queryPublicKey.publicKeyB64()).thenReturn("X-AS-base64");

        mvc.perform(get("/blind-sign/public-key"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.publicKeyB64").value("X-AS-base64"));
    }

    /** En ejecución real la cadena la valida Tomcat con el truststore; aquí no se valida. */
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
