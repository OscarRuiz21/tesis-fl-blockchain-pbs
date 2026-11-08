package org.fl.as.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.fl.as.controller.AuthDtos.AdminInitRequest;
import org.fl.as.exception.AdminAccessDeniedException;
import org.fl.as.model.ClientId;
import org.fl.as.security.CertSubjectExtractor;
import org.fl.as.service.RegisterEligibilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.X509Certificate;

/**
 * {@code POST /admin/init} carga la whitelist de clientes y el total de
 * rondas del experimento, y reinicia la ronda activa a 1. El avance de ronda
 * no se administra aquí: el AS lo infiere de las propias peticiones de
 * credencial (ver {@link org.fl.as.store.EligibilityRegistry}), de modo que el
 * coordinator solo instrumenta y manda {@code start}.
 *
 * <p>Sin certificado mTLS se responde 401; con un CN distinto de
 * {@code auth.admin-cn}, 403. Sin la segunda verificación cualquier cliente
 * con certificado válido podría sobrescribir la whitelist a mitad de la
 * corrida.</p>
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final RegisterEligibilityService registerEligibility;

    @Value("${auth.admin-cn:coordinator}")
    private String adminCn;

    @PostMapping("/init")
    public ResponseEntity<Void> init(@Valid @RequestBody AdminInitRequest req,
                                     HttpServletRequest http) {
        X509Certificate cert = CertSubjectExtractor.requireClientCert(http);
        String cn = CertSubjectExtractor.extractCommonName(cert);

        if (!adminCn.equals(cn)) {
            // El CN ofensor va al log, no al cuerpo del 403.
            throw new AdminAccessDeniedException(cn);
        }

        log.info("Admin init received: experimentId={} clients={} totalRounds={}",
            req.experimentId(), req.allowedClients().size(), req.totalRounds());

        registerEligibility.register(
            req.allowedClients().stream().map(ClientId::of).toList(),
            req.totalRounds());
        return ResponseEntity.ok().build();
    }
}
