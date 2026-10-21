package com.zodiakk.apigateway.security.filter;

import com.zodiakk.apigateway.client.auth.AuthServiceClient;
import com.zodiakk.apigateway.client.auth.dto.response.TokenValidationResponseDto;
import com.zodiakk.apigateway.common.constants.Headers;
import com.zodiakk.apigateway.common.exception.AuthServiceUnavailableException;
import com.zodiakk.apigateway.common.exception.ErrorMessages;
import com.zodiakk.apigateway.common.handler.ErrorResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private ErrorResponseWriter errorResponseWriter;

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(authServiceClient, errorResponseWriter);
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
        lenient().when(errorResponseWriter.write(any(), any(), anyString(), anyString()))
                .thenReturn(Mono.empty());
    }

    @ParameterizedTest(name = "{0} {1} is public")
    @MethodSource("publicPaths")
    @DisplayName("public paths bypass token validation")
    void publicPathsBypassTokenValidation(HttpMethod method, String path) {
        MockServerWebExchange exchange = exchange(method, path, Map.of(
                Headers.USER_ID, "spoofed-id",
                Headers.USER_ROLE, "ADMIN",
                Headers.USER_EMAIL, "spoofed@example.com",
                Headers.INTERNAL_KEY, "spoofed-key"
        ));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        verifyNoInteractions(authServiceClient, errorResponseWriter);

        var forwardedHeaders = captor.getValue().getRequest().getHeaders();
        assertThat(forwardedHeaders.getFirst(Headers.USER_ID)).isNull();
        assertThat(forwardedHeaders.getFirst(Headers.USER_ROLE)).isNull();
        assertThat(forwardedHeaders.getFirst(Headers.USER_EMAIL)).isNull();
        assertThat(forwardedHeaders.getFirst(Headers.INTERNAL_KEY)).isNull();
    }

    static Stream<Arguments> publicPaths() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/swagger-ui.html"),
                Arguments.of(HttpMethod.GET, "/swagger-ui/index.html"),
                Arguments.of(HttpMethod.GET, "/v3/api-docs"),
                Arguments.of(HttpMethod.GET, "/v3/api-docs/swagger-config"),
                Arguments.of(HttpMethod.GET, "/api-docs"),
                Arguments.of(HttpMethod.GET, "/auth-service/api-docs"),
                Arguments.of(HttpMethod.GET, "/employee-service/api-docs"),
                Arguments.of(HttpMethod.GET, "/actuator/health"),
                Arguments.of(HttpMethod.GET, "/actuator/health/liveness"),
                Arguments.of(HttpMethod.GET, "/actuator/info"),
                Arguments.of(HttpMethod.GET, "/error"),
                Arguments.of(HttpMethod.POST, "/api/auth/login"),
                Arguments.of(HttpMethod.POST, "/api/auth/refresh"),
                Arguments.of(HttpMethod.POST, "/api/auth/change-initial-password"),
                Arguments.of(HttpMethod.OPTIONS, "/api/employees")
        );
    }

    @Test
    @DisplayName("protected path without Authorization returns 401")
    void missingAuthorizationReturnsUnauthorized() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/employees", Map.of());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(chain, authServiceClient);
        verify(errorResponseWriter).write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                ErrorMessages.AUTHORIZATION_HEADER_MISSING
        );
    }

    @ParameterizedTest(name = "Authorization: [{0}]")
    @MethodSource("invalidAuthorizationHeaders")
    @DisplayName("invalid Authorization header returns 401")
    void invalidAuthorizationHeaderReturnsUnauthorized(String authorization) {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/employees", Map.of(
                Headers.AUTHORIZATION, authorization
        ));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(chain, authServiceClient);
        verify(errorResponseWriter).write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                ErrorMessages.AUTHORIZATION_HEADER_INVALID
        );
    }

    static Stream<String> invalidAuthorizationHeaders() {
        return Stream.of(
                "Basic dXNlcjpwYXNzd29yZA==",
                "bearer token",
                "Bearer",
                "Bearer    "
        );
    }

    @Test
    @DisplayName("valid token is validated and user headers are added")
    void validTokenAddsUserHeaders() {
        when(authServiceClient.validateToken("jwt-token"))
                .thenReturn(Mono.just(validValidation("EMPLOYEE")));

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/employees", Map.of(
                Headers.AUTHORIZATION, "Bearer jwt-token",
                Headers.USER_ID, "spoofed-id",
                Headers.USER_ROLE, "ADMIN",
                Headers.USER_EMAIL, "spoofed@example.com",
                Headers.INTERNAL_KEY, "spoofed-key"
        ));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        var headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.getFirst(Headers.AUTHORIZATION)).isEqualTo("Bearer jwt-token");
        assertThat(headers.getFirst(Headers.USER_ID)).isEqualTo(USER_ID.toString());
        assertThat(headers.getFirst(Headers.USER_ROLE)).isEqualTo("ROLE_EMPLOYEE");
        assertThat(headers.getFirst(Headers.USER_EMAIL)).isEqualTo("user@example.com");
        assertThat(headers.getFirst(Headers.INTERNAL_KEY)).isNull();
        verifyNoInteractions(errorResponseWriter);
    }

    @Test
    @DisplayName("already prefixed role is not prefixed twice")
    void alreadyPrefixedRoleIsPreserved() {
        when(authServiceClient.validateToken("jwt-token"))
                .thenReturn(Mono.just(validValidation("ROLE_ADMIN")));

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/employees", Map.of(
                Headers.AUTHORIZATION, "Bearer jwt-token"
        ));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst(Headers.USER_ROLE))
                .isEqualTo("ROLE_ADMIN");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValidations")
    @DisplayName("incomplete or invalid validation returns 401 INVALID_TOKEN")
    void invalidValidationReturnsInvalidToken(String description, TokenValidationResponseDto validation) {
        when(authServiceClient.validateToken("jwt-token")).thenReturn(Mono.just(validation));

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/employees", Map.of(
                Headers.AUTHORIZATION, "Bearer jwt-token"
        ));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(chain);
        verify(errorResponseWriter).write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN",
                ErrorMessages.INVALID_TOKEN
        );
    }

    static Stream<Arguments> invalidValidations() {
        return Stream.of(
                Arguments.of("valid=false", TokenValidationResponseDto.builder()
                        .valid(false)
                        .userId(USER_ID)
                        .role("EMPLOYEE")
                        .email("user@example.com")
                        .build()),
                Arguments.of("null userId", TokenValidationResponseDto.builder()
                        .valid(true)
                        .role("EMPLOYEE")
                        .email("user@example.com")
                        .build()),
                Arguments.of("null role", TokenValidationResponseDto.builder()
                        .valid(true)
                        .userId(USER_ID)
                        .email("user@example.com")
                        .build()),
                Arguments.of("null email", TokenValidationResponseDto.builder()
                        .valid(true)
                        .userId(USER_ID)
                        .role("EMPLOYEE")
                        .build())
        );
    }

    @Test
    @DisplayName("empty auth service response is treated as invalid token")
    void emptyValidationReturnsInvalidToken() {
        when(authServiceClient.validateToken("jwt-token")).thenReturn(Mono.empty());

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/employees", Map.of(
                Headers.AUTHORIZATION, "Bearer jwt-token"
        ));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(chain);
        verify(errorResponseWriter).write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN",
                ErrorMessages.INVALID_TOKEN
        );
    }

    @Test
    @DisplayName("auth service failure returns 503")
    void authServiceFailureReturnsServiceUnavailable() {
        when(authServiceClient.validateToken("jwt-token"))
                .thenReturn(Mono.error(new AuthServiceUnavailableException(
                        "auth is down",
                        new RuntimeException("connection refused")
                )));

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/employees", Map.of(
                Headers.AUTHORIZATION, "Bearer jwt-token"
        ));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(chain);
        verify(errorResponseWriter).write(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTH_SERVICE_UNAVAILABLE",
                ErrorMessages.AUTH_SERVICE_UNAVAILABLE
        );
    }

    @Test
    @DisplayName("filter runs before routing")
    void filterOrderIsMinusOneHundred() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    private static TokenValidationResponseDto validValidation(String role) {
        return TokenValidationResponseDto.builder()
                .valid(true)
                .userId(USER_ID)
                .role(role)
                .email("user@example.com")
                .build();
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path, Map<String, String> headers) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path);
        headers.forEach((name, value) -> builder.header(name, value));
        return MockServerWebExchange.from(builder.build());
    }
}
