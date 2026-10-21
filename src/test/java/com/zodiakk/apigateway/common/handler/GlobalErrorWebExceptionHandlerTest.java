package com.zodiakk.apigateway.common.handler;

import com.zodiakk.apigateway.common.exception.ErrorMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalErrorWebExceptionHandlerTest {

    @Mock
    private ErrorResponseWriter errorResponseWriter;

    private GlobalErrorWebExceptionHandler handler;

    private final ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/employees").build()
    );

    @BeforeEach
    void setUp() {
        handler = new GlobalErrorWebExceptionHandler(errorResponseWriter);
        when(errorResponseWriter.write(any(), any(), anyString(), anyString()))
                .thenReturn(Mono.empty());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("statusMappings")
    @DisplayName("maps HTTP statuses to gateway error codes")
    void mapsHttpStatuses(HttpStatus status, String code, String message) {
        StepVerifier.create(handler.handle(exchange, new ResponseStatusException(status)))
                .verifyComplete();

        verify(errorResponseWriter).write(exchange, status, code, message);
    }

    static Stream<Arguments> statusMappings() {
        return Stream.of(
                Arguments.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ErrorMessages.UNAUTHORIZED),
                Arguments.of(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", ErrorMessages.ROUTE_NOT_FOUND),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ErrorMessages.SERVICE_UNAVAILABLE),
                Arguments.of(HttpStatus.BAD_GATEWAY, "SERVICE_UNAVAILABLE", ErrorMessages.SERVICE_UNAVAILABLE),
                Arguments.of(HttpStatus.BAD_REQUEST, "INTERNAL_SERVER_ERROR", ErrorMessages.INTERNAL_SERVER_ERROR),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", ErrorMessages.INTERNAL_SERVER_ERROR)
        );
    }

    @Test
    @DisplayName("unknown status code becomes 500")
    void unknownStatusCodeBecomesInternalServerError() {
        var exception = new ResponseStatusException(HttpStatusCode.valueOf(599));

        StepVerifier.create(handler.handle(exchange, exception))
                .verifyComplete();

        verify(errorResponseWriter).write(
                exchange,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                ErrorMessages.INTERNAL_SERVER_ERROR
        );
    }

    @Test
    @DisplayName("unexpected exception becomes 500")
    void unexpectedExceptionBecomesInternalServerError() {
        StepVerifier.create(handler.handle(exchange, new RuntimeException("boom")))
                .verifyComplete();

        verify(errorResponseWriter).write(
                exchange,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                ErrorMessages.INTERNAL_SERVER_ERROR
        );
    }
}
