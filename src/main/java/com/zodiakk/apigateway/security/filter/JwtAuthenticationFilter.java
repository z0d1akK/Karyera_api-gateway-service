package com.zodiakk.apigateway.security.filter;

import com.zodiakk.apigateway.client.auth.AuthServiceClient;
import com.zodiakk.apigateway.client.auth.dto.response.TokenValidationResponseDto;
import com.zodiakk.apigateway.common.constants.ApiPaths;
import com.zodiakk.apigateway.common.constants.Headers;
import com.zodiakk.apigateway.common.constants.SecurityConstants;
import com.zodiakk.apigateway.common.constants.SecurityPaths;
import com.zodiakk.apigateway.common.exception.AuthServiceUnavailableException;
import com.zodiakk.apigateway.common.exception.ErrorMessages;
import com.zodiakk.apigateway.common.handler.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final AuthServiceClient authServiceClient;

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = sanitize(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        if (isPublicPath(sanitizedRequest)) {
            return chain.filter(sanitizedExchange);
        }

        String authHeader = sanitizedRequest.getHeaders().getFirst(Headers.AUTHORIZATION);

        if (authHeader == null) {
            return unauthorized(exchange, ErrorMessages.AUTHORIZATION_HEADER_MISSING);
        }

        if (!authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return unauthorized(exchange, ErrorMessages.AUTHORIZATION_HEADER_INVALID);
        }

        String token = authHeader.substring(SecurityConstants.BEARER_PREFIX.length()).trim();

        if (token.isEmpty()) {
            return unauthorized(exchange, ErrorMessages.AUTHORIZATION_HEADER_INVALID);
        }

        return authServiceClient.validateToken(token)
                .defaultIfEmpty(TokenValidationResponseDto.builder().valid(false).build())
                .flatMap(validation -> {
                    if (!isValid(validation)) {
                        return errorResponseWriter.write(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                "INVALID_TOKEN",
                                ErrorMessages.INVALID_TOKEN
                        );
                    }

                    return chain.filter(
                            exchange.mutate()
                                    .request(addUserHeaders(sanitizedRequest, validation))
                                    .build()
                    );
                })
                .onErrorResume(AuthServiceUnavailableException.class, ex ->
                        errorResponseWriter.write(
                                exchange,
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "AUTH_SERVICE_UNAVAILABLE",
                                ErrorMessages.AUTH_SERVICE_UNAVAILABLE
                        )
                );
    }

    private boolean isValid(TokenValidationResponseDto validation) {
        return validation != null
                && Boolean.TRUE.equals(validation.getValid())
                && validation.getUserId() != null
                && validation.getRole() != null
                && validation.getEmail() != null;
    }

    private ServerHttpRequest sanitize(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(Headers.USER_ID);
                    headers.remove(Headers.USER_ROLE);
                    headers.remove(Headers.USER_EMAIL);
                    headers.remove(Headers.INTERNAL_KEY);
                })
                .build();
    }

    private ServerHttpRequest addUserHeaders(ServerHttpRequest request, TokenValidationResponseDto validation) {
        return request.mutate()
                .header(Headers.USER_ID, validation.getUserId().toString())
                .header(Headers.USER_ROLE, toAuthority(validation.getRole()))
                .header(Headers.USER_EMAIL, validation.getEmail())
                .build();
    }

    private String toAuthority(String role) {
        return role.startsWith(SecurityConstants.ROLE_PREFIX)
                ? role
                : SecurityConstants.ROLE_PREFIX + role;
    }

    private boolean isPublicPath(ServerHttpRequest request) {
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return true;
        }

        String path = request.getURI().getPath();

        if (request.getMethod() == HttpMethod.POST
                && (path.equals(ApiPaths.LOGIN)
                || path.equals(ApiPaths.REFRESH)
                || path.equals(ApiPaths.CHANGE_INITIAL_PASSWORD))) {
            return true;
        }

        return path.equals(SecurityPaths.SWAGGER_HTML)
                || path.startsWith(SecurityPaths.SWAGGER_UI)
                || path.startsWith(SecurityPaths.API_DOCS)
                || path.startsWith(SecurityPaths.GATEWAY_API_DOCS)
                || path.startsWith(SecurityPaths.AUTH_DOCS)
                || path.startsWith(SecurityPaths.EMPLOYEE_DOCS)
                || path.startsWith(SecurityPaths.PROJECT_DOCS)
                || path.equals(SecurityPaths.ACTUATOR_HEALTH)
                || path.startsWith(SecurityPaths.ACTUATOR_HEALTH + "/")
                || path.equals(SecurityPaths.ACTUATOR_INFO)
                || path.equals(SecurityPaths.ERROR);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return errorResponseWriter.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                message
        );
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
