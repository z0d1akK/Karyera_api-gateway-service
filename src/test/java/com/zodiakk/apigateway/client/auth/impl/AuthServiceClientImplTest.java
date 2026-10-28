package com.zodiakk.apigateway.client.auth.impl;

import com.zodiakk.apigateway.client.auth.AuthServiceClient;
import com.zodiakk.apigateway.common.exception.AuthServiceUnavailableException;
import com.zodiakk.apigateway.common.exception.ErrorMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceClientImplTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @DisplayName("validates token via the internal auth endpoint")
    void validatesTokenViaInternalEndpoint() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        AuthServiceClient client = client(request -> {
            captured.set(request);
            return Mono.just(jsonResponse(HttpStatus.OK, """
                    {"valid":true,"userId":"%s","role":"EMPLOYEE","email":"user@example.com"}
                    """.formatted(USER_ID)));
        });

        StepVerifier.create(client.validateToken("jwt-token"))
                .assertNext(validation -> {
                    assertThat(validation.getValid()).isTrue();
                    assertThat(validation.getUserId()).isEqualTo(USER_ID);
                    assertThat(validation.getRole()).isEqualTo("EMPLOYEE");
                    assertThat(validation.getEmail()).isEqualTo("user@example.com");
                })
                .verifyComplete();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.get().url().getPath()).isEqualTo("/internal/v1/auth/validate");
    }

    @Test
    @DisplayName("maps auth service 5xx to AuthServiceUnavailableException")
    void mapsServerErrorToUnavailableException() {
        AuthServiceClient client = client(request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        StepVerifier.create(client.validateToken("jwt-token"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthServiceUnavailableException.class);
                    assertThat(error).hasMessage(ErrorMessages.AUTH_SERVICE_UNAVAILABLE);
                })
                .verify();
    }

    @Test
    @DisplayName("maps invalid auth service response to AuthServiceUnavailableException")
    void mapsDecodingErrorToUnavailableException() {
        AuthServiceClient client = client(request ->
                Mono.just(jsonResponse(HttpStatus.OK, "not-a-json")));

        StepVerifier.create(client.validateToken("jwt-token"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthServiceUnavailableException.class);
                    assertThat(error).hasMessage(ErrorMessages.AUTH_SERVICE_UNAVAILABLE);
                })
                .verify();
    }

    @Test
    @DisplayName("empty auth service response completes empty")
    void emptyResponseCompletesEmpty() {
        AuthServiceClient client = client(request ->
                Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()));

        StepVerifier.create(client.validateToken("jwt-token"))
                .verifyComplete();
    }

    private static AuthServiceClient client(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        return new AuthServiceClientImpl(webClient);
    }

    private static ClientResponse jsonResponse(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}
