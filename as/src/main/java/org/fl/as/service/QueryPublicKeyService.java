package org.fl.as.service;

import lombok.RequiredArgsConstructor;
import org.fl.as.crypto.CryptoCodec;
import org.fl.as.model.ASKeyPair;
import org.springframework.stereotype.Service;

/**
 * Los clientes consultan {@code X_AS} al inicio del experimento; el chaincode
 * la recibe aparte en {@code initProtocol(...)}.
 */
@Service
@RequiredArgsConstructor
public class QueryPublicKeyService {

    private final ASKeyPair keyPair;

    public String publicKeyB64() {
        return CryptoCodec.encodePoint(keyPair.publicKey());
    }
}
