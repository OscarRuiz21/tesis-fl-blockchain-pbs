package org.fl.as.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.fl.as.controller.AuthDtos.CommitRequest;
import org.fl.as.controller.AuthDtos.CommitResponse;
import org.fl.as.controller.AuthDtos.PublicKeyResponse;
import org.fl.as.controller.AuthDtos.SignRequest;
import org.fl.as.controller.AuthDtos.SignResponse;
import org.fl.as.model.ClientId;
import org.fl.as.model.CommitmentIssued;
import org.fl.as.model.RoundId;
import org.fl.as.model.SessionId;
import org.fl.as.model.SignatureIssued;
import org.fl.as.security.CertSubjectExtractor;
import org.fl.as.service.IssueCommitmentService;
import org.fl.as.service.IssueSignatureService;
import org.fl.as.service.QueryPublicKeyService;
import org.fl.as.service.VerifyClientIdentityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.X509Certificate;

/**
 * Emisión de credenciales (Etapa 1): {@code /commit} abre la sesión y
 * devuelve {@code (A, B)}; {@code /sign} firma {@code e} y consume la sesión.
 */
@RestController
@RequestMapping("/blind-sign")
@RequiredArgsConstructor
public class BlindSignController {

    private final IssueCommitmentService issueCommitment;
    private final IssueSignatureService issueSignature;
    private final QueryPublicKeyService queryPublicKey;
    private final VerifyClientIdentityService verifyClientIdentity;

    @PostMapping("/commit")
    public CommitResponse commit(@Valid @RequestBody CommitRequest req,
                                  HttpServletRequest http) {
        // El CN del certificado debe coincidir con el clientId declarado; así un
        // cliente no puede pedir credencial a nombre de otro.
        X509Certificate cert = CertSubjectExtractor.requireClientCert(http);
        String cn = CertSubjectExtractor.extractCommonName(cert);
        verifyClientIdentity.verify(cn, req.clientId());

        CommitmentIssued result = issueCommitment.issue(
            ClientId.of(req.clientId()),
            RoundId.of(req.roundId()));
        return new CommitResponse(
            result.sessionId().asString(),
            result.aB64(),
            result.bB64());
    }

    @PostMapping("/sign")
    public SignResponse sign(@Valid @RequestBody SignRequest req,
                              HttpServletRequest http) {
        // /sign no recibe clientId, así que no hay CN que comparar: el sessionId
        // solo lo conoce quien hizo /commit.
        CertSubjectExtractor.requireClientCert(http);
        SignatureIssued result = issueSignature.sign(
            SessionId.of(req.sessionId()),
            req.eB64());
        return new SignResponse(
            result.rAnsB64(),
            result.cB64(),
            result.sB64(),
            result.dB64());
    }

    @GetMapping("/public-key")
    public PublicKeyResponse getPublicKey() {
        // Sin certificado: X_AS es pública.
        return new PublicKeyResponse(queryPublicKey.publicKeyB64());
    }
}
