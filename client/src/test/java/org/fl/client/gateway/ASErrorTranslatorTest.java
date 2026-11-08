package org.fl.client.gateway;

import org.fl.client.exception.AuthenticationFailedException;
import org.fl.client.exception.ClientNotEligibleException;
import org.fl.client.exception.DuplicateCredentialRequestException;
import org.fl.client.exception.SessionNotFoundException;
import org.fl.client.model.ClientId;
import org.fl.client.model.RoundId;
import org.fl.client.model.SessionId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;

class ASErrorTranslatorTest {

    private final ASErrorTranslator translator = new ASErrorTranslator();
    private final ClientId client = ClientId.of("client-1");
    private final RoundId round = RoundId.of(1);
    private final SessionId session = SessionId.random();

    @Test
    void commit401MapsToClientNotEligible() {
        var http = HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null);

        var ex = translator.translateCommit(http, client, round);

        assertThat(ex).isInstanceOf(ClientNotEligibleException.class);
    }

    @Test
    void commit409MapsToDuplicate() {
        var http = HttpClientErrorException.create(
            HttpStatus.CONFLICT, "Conflict", null, null, null);

        var ex = translator.translateCommit(http, client, round);

        assertThat(ex).isInstanceOf(DuplicateCredentialRequestException.class);
    }

    @Test
    void sign404MapsToSessionNotFound() {
        var http = HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not found", null, null, null);

        var ex = translator.translateSign(http, session);

        assertThat(ex).isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void networkErrorMapsToAuthenticationFailed() {
        var ex = translator.translateCommit(
            new RestClientException("ECONNREFUSED"), client, round);

        assertThat(ex).isInstanceOf(AuthenticationFailedException.class);
    }
}
