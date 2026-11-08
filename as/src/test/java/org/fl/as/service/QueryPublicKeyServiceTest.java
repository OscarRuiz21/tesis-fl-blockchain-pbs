package org.fl.as.service;

import org.fl.as.crypto.CryptoCodec;
import org.fl.as.crypto.PBSDomainParams;
import org.fl.as.model.ASKeyPair;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPublicKeyServiceTest {

    @Test
    void shouldReturnEncodedPublicKey() {
        PBSDomainParams params = PBSDomainParams.p256();
        ASKeyPair keyPair = ASKeyPair.generate(params, new SecureRandom());
        QueryPublicKeyService service = new QueryPublicKeyService(keyPair);

        String b64 = service.publicKeyB64();

        // 65 bytes sin comprimir son 88 caracteres en Base64.
        assertThat(b64).hasSize(88);
        assertThat(CryptoCodec.decodePoint(b64, params.curve()).normalize())
            .isEqualTo(keyPair.publicKey().normalize());
    }
}
