package org.fl.client.gateway;

import org.fl.client.exception.AuthenticationFailedException;
import org.fl.client.exception.ClientNotEligibleException;
import org.fl.client.exception.DuplicateCredentialRequestException;
import org.fl.client.exception.SessionNotFoundException;
import org.fl.client.model.ClientId;
import org.fl.client.model.RoundId;
import org.fl.client.model.SessionId;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/** Traduce los códigos HTTP que define el {@code GlobalExceptionHandler} del AS. */
@Component
public class ASErrorTranslator {

    public RuntimeException translateCommit(RestClientException ex, ClientId clientId, RoundId roundId) {
        if (ex instanceof HttpClientErrorException http) {
            return switch (http.getStatusCode().value()) {
                case 401 -> new ClientNotEligibleException(clientId);
                case 409 -> new DuplicateCredentialRequestException(clientId, roundId);
                default -> new AuthenticationFailedException(
                    "AS commit failed with HTTP " + http.getStatusCode().value(), ex);
            };
        }
        return new AuthenticationFailedException("AS commit network error", ex);
    }

    public RuntimeException translateSign(RestClientException ex, SessionId sessionId) {
        if (ex instanceof HttpClientErrorException http) {
            return switch (http.getStatusCode().value()) {
                case 404 -> new SessionNotFoundException(sessionId);
                default -> new AuthenticationFailedException(
                    "AS sign failed with HTTP " + http.getStatusCode().value(), ex);
            };
        }
        return new AuthenticationFailedException("AS sign network error", ex);
    }

    public RuntimeException translatePubKey(RestClientException ex) {
        if (ex instanceof HttpClientErrorException http) {
            return new AuthenticationFailedException(
                "AS public-key fetch failed with HTTP " + http.getStatusCode().value(), ex);
        }
        return new AuthenticationFailedException("AS public-key network error", ex);
    }
}
