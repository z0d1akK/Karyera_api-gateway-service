package com.zodiakk.apigateway;

import com.zodiakk.apigateway.common.constants.Headers;
import com.zodiakk.apigateway.common.exception.ErrorMessages;
import com.zodiakk.apigateway.support.StubHttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static StubHttpServer authService;
    private static StubHttpServer employeeService;

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeAll
    static void startStubServices() {
        ensureStubServicesStarted();
    }

    @AfterAll
    static void stopStubServices() {
        if (authService != null) {
            authService.close();
        }
        if (employeeService != null) {
            employeeService.close();
        }
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        ensureStubServicesStarted();

        registry.add("services.auth.url", authService::baseUrl);
        registry.add("services.employee.url", employeeService::baseUrl);
        registry.add("internal.api-key", () -> "test-internal-api-key");
        registry.add("cors.allowed-origins", () -> "http://localhost:5173");
    }

    private static synchronized void ensureStubServicesStarted() {
        if (authService == null) {
            authService = new StubHttpServer();
        }
        if (employeeService == null) {
            employeeService = new StubHttpServer();
        }
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        authService.reset();
        employeeService.reset();
    }

    @Test
    @DisplayName("public login is proxied without token validation")
    void publicLoginIsProxiedWithoutValidation() {
        authService.respond("/api/v1/auth/login", 200, "{\"accessToken\":\"access-token\"}");

        client.post()
                .uri("/api/v1/auth/login")
                .header(Headers.USER_ID, "spoofed-id")
                .header(Headers.USER_ROLE, "ADMIN")
                .header(Headers.USER_EMAIL, "spoofed@example.com")
                .header(Headers.INTERNAL_KEY, "spoofed-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"user@example.com\",\"password\":\"secret\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("access-token");

        StubHttpServer.RecordedRequest request = authService.takeRequest();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v1/auth/login");
        assertThat(request.header(Headers.USER_ID)).isNull();
        assertThat(request.header(Headers.USER_ROLE)).isNull();
        assertThat(request.header(Headers.USER_EMAIL)).isNull();
        assertThat(request.header(Headers.INTERNAL_KEY)).isNull();
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("protected endpoint without token returns 401")
    void protectedEndpointWithoutTokenReturnsUnauthorized() {
        client.get()
                .uri("/api/v1/employees")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.valid").isEqualTo(false)
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo(ErrorMessages.AUTHORIZATION_HEADER_MISSING);

        assertThat(authService.pollRequest()).isNull();
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("non-Bearer Authorization header returns 401")
    void nonBearerAuthorizationReturnsUnauthorized() {
        client.get()
                .uri("/api/v1/employees")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNzd29yZA==")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo(ErrorMessages.AUTHORIZATION_HEADER_INVALID);

        assertThat(authService.pollRequest()).isNull();
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("valid token is validated and forwarded with trusted user headers")
    void validTokenAddsTrustedUserHeaders() {
        authService.respond("/internal/v1/auth/validate", 200, """
                {"valid":true,"userId":"%s","role":"EMPLOYEE","email":"user@example.com"}
                """.formatted(USER_ID));
        employeeService.respond("/api/v1/employees", 200, "{\"content\":[]}");

        client.get()
                .uri("/api/v1/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header(Headers.USER_ID, "spoofed-id")
                .header(Headers.USER_ROLE, "ADMIN")
                .header(Headers.USER_EMAIL, "spoofed@example.com")
                .header(Headers.INTERNAL_KEY, "spoofed-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray();

        StubHttpServer.RecordedRequest authRequest = authService.takeRequest();
        assertThat(authRequest).isNotNull();
        assertThat(authRequest.method()).isEqualTo("POST");
        assertThat(authRequest.path()).isEqualTo("/internal/v1/auth/validate");
        assertThat(authRequest.header(Headers.INTERNAL_KEY)).isEqualTo("test-internal-api-key");
        assertThat(authRequest.body()).contains("valid-token");

        StubHttpServer.RecordedRequest employeeRequest = employeeService.takeRequest();
        assertThat(employeeRequest).isNotNull();
        assertThat(employeeRequest.method()).isEqualTo("GET");
        assertThat(employeeRequest.path()).isEqualTo("/api/v1/employees");
        assertThat(employeeRequest.header(Headers.USER_ID)).isEqualTo(USER_ID.toString());
        assertThat(employeeRequest.header(Headers.USER_ROLE)).isEqualTo("ROLE_EMPLOYEE");
        assertThat(employeeRequest.header(Headers.USER_EMAIL)).isEqualTo("user@example.com");
        assertThat(employeeRequest.header(Headers.INTERNAL_KEY)).isNull();
        assertThat(employeeRequest.header(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer valid-token");
    }

    @Test
    @DisplayName("token rejected by auth service returns 401 INVALID_TOKEN")
    void invalidTokenReturnsInvalidToken() {
        authService.respond("/internal/v1/auth/validate", 200, "{\"valid\":false}");

        client.get()
                .uri("/api/v1/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_TOKEN")
                .jsonPath("$.message").isEqualTo(ErrorMessages.INVALID_TOKEN);

        assertThat(authService.takeRequest()).isNotNull();
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("empty auth service response is rejected")
    void emptyAuthResponseIsRejected() {
        authService.respond("/internal/v1/auth/validate", 200, "");

        client.get()
                .uri("/api/v1/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_TOKEN")
                .jsonPath("$.message").isEqualTo(ErrorMessages.INVALID_TOKEN);

        assertThat(authService.takeRequest()).isNotNull();
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("auth service user routes are proxied after validation")
    void authServiceUserRoutesAreProxied() {
        authService.respond("/internal/v1/auth/validate", 200, """
                {"valid":true,"userId":"%s","role":"ADMIN","email":"admin@example.com"}
                """.formatted(USER_ID));
        authService.respond("/api/v1/users/123", 200, "{\"id\":\"123\"}");

        client.get()
                .uri("/api/v1/users/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("123");

        StubHttpServer.RecordedRequest validationRequest = authService.takeRequest();
        assertThat(validationRequest).isNotNull();
        assertThat(validationRequest.path()).isEqualTo("/internal/v1/auth/validate");

        StubHttpServer.RecordedRequest usersRequest = authService.takeRequest();
        assertThat(usersRequest).isNotNull();
        assertThat(usersRequest.path()).isEqualTo("/api/v1/users/123");
        assertThat(usersRequest.header(Headers.USER_ROLE)).isEqualTo("ROLE_ADMIN");
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("auth service failure returns 503")
    void authServiceFailureReturnsServiceUnavailable() {
        authService.respond("/internal/v1/auth/validate", 500, "{}");

        client.get()
                .uri("/api/v1/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTH_SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo(ErrorMessages.AUTH_SERVICE_UNAVAILABLE);

        assertThat(authService.takeRequest()).isNotNull();
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("service api docs are routed and bypass authentication")
    void serviceApiDocsArePublic() {
        authService.respond("/v3/api-docs", 200, "{\"openapi\":\"3.0.1\"}");

        client.get()
                .uri("/auth-service/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openapi").isEqualTo("3.0.1");

        StubHttpServer.RecordedRequest request = authService.takeRequest();
        assertThat(request).isNotNull();
        assertThat(request.path()).isEqualTo("/v3/api-docs");
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("CORS preflight is handled without authentication")
    void corsPreflightIsHandledWithoutAuthentication() {
        client.options()
                .uri("/api/v1/employees")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                )
                .expectHeader().valueMatches(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        ".*GET.*"
                );

        assertThat(authService.pollRequest()).isNull();
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("unknown route returns 404")
    void unknownRouteReturnsNotFound() {
        client.get()
                .uri("/api/unknown")
                .exchange()
                .expectStatus().isNotFound();

        assertThat(authService.pollRequest()).isNull();
        assertThat(employeeService.pollRequest()).isNull();
    }

    @Test
    @DisplayName("incomplete validation response is rejected")
    void incompleteValidationResponseIsRejected() {
        authService.respond("/internal/v1/auth/validate", 200,
                "{\"valid\":true,\"role\":\"EMPLOYEE\",\"email\":\"user@example.com\"}");

        client.get()
                .uri("/api/v1/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_TOKEN");

        assertThat(employeeService.pollRequest()).isNull();
    }
}
