package org.fl.as.service;

import org.fl.as.exception.ClientIdMismatchException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifyClientIdentityServiceTest {

    private final VerifyClientIdentityService service = new VerifyClientIdentityService();

    @Test
    void matchingCnAndClientIdPasses() {
        assertThatNoException().isThrownBy(() ->
            service.verify("User1@org2.fl.network", "client-1"));
    }

    @Test
    void matchingMultiDigitClientIdPasses() {
        assertThatNoException().isThrownBy(() ->
            service.verify("User20@org2.fl.network", "client-20"));
    }

    @Test
    void mismatchCertCnRejected() {
        assertThatThrownBy(() ->
            service.verify("User2@org2.fl.network", "client-1"))
            .isInstanceOf(ClientIdMismatchException.class)
            .hasMessageContaining("User2@org2.fl.network")
            .hasMessageContaining("client-1");
    }

    @Test
    void mismatchExceptionExposesBothFieldsForAudit() {
        // Ambos campos van al log; el cuerpo del 401 no expone el CN.
        try {
            service.verify("User9@org2.fl.network", "client-1");
        } catch (ClientIdMismatchException ex) {
            assertThat(ex.getDeclaredClientId()).isEqualTo("client-1");
            assertThat(ex.getCertCn()).isEqualTo("User9@org2.fl.network");
        }
    }

    @Test
    void nullClientIdRejected() {
        assertThatThrownBy(() ->
            service.verify("User1@org2.fl.network", null))
            .isInstanceOf(ClientIdMismatchException.class);
    }

    @Test
    void clientIdWithoutPrefixRejected() {
        assertThatThrownBy(() ->
            service.verify("User1@org2.fl.network", "foo-bar"))
            .isInstanceOf(ClientIdMismatchException.class);
    }

    @Test
    void emptyClientIdRejected() {
        assertThatThrownBy(() ->
            service.verify("User1@org2.fl.network", ""))
            .isInstanceOf(ClientIdMismatchException.class);
    }

    @Test
    void crossClientImpersonationCanonical() {
        assertThatThrownBy(() ->
            service.verify("User2@org2.fl.network", "client-1"))
            .isInstanceOf(ClientIdMismatchException.class);
    }
}
