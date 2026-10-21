package com.zodiakk.apigateway.common.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseWriterTest {

    private final ErrorResponseWriter writer = new ErrorResponseWriter();

    @Test
    @DisplayName("writes 401 JSON with valid=false")
    void writesUnauthorizedResponse() {
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(writer.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "test message"))
                .verifyComplete();

        var response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        String body = response.getBodyAsString().block();
        assertThat(body)
                .contains("\"valid\":false")
                .contains("\"status\":401")
                .contains("\"code\":\"UNAUTHORIZED\"")
                .contains("\"error\":\"Unauthorized\"")
                .contains("\"message\":\"test message\"")
                .contains("\"timestamp\"");
    }

    @Test
    @DisplayName("omits valid for non-401 errors")
    void omitsValidForServiceUnavailable() {
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(writer.write(
                        exchange,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVICE_UNAVAILABLE",
                        "service is down"
                ))
                .verifyComplete();

        var response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        String body = response.getBodyAsString().block();
        assertThat(body)
                .doesNotContain("\"valid\"")
                .contains("\"status\":503")
                .contains("\"code\":\"SERVICE_UNAVAILABLE\"")
                .contains("\"message\":\"service is down\"");
    }

    @Test
    @DisplayName("does not write when response is already committed")
    void doesNotWriteCommittedResponse() {
        MockServerWebExchange exchange = exchange();
        exchange.getResponse().setComplete().block();

        StepVerifier.create(writer.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "test"))
                .verifyComplete();

        assertThat(exchange.getResponse().getBodyAsString().block()).isEmpty();
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/employees").build());
    }
}
