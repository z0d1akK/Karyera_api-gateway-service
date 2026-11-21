package com.zodiakk.apigateway.client.auth.impl;

import com.zodiakk.apigateway.client.auth.AuthServiceClient;
import com.zodiakk.apigateway.client.auth.dto.request.ValidateTokenRequestDto;
import com.zodiakk.apigateway.client.auth.dto.response.TokenValidationResponseDto;
import com.zodiakk.apigateway.common.constants.SecurityPaths;
import com.zodiakk.apigateway.common.exception.AuthServiceUnavailableException;
import com.zodiakk.apigateway.common.exception.ErrorMessages;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class AuthServiceClientImpl implements AuthServiceClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient authWebClient;

    private final RateLimiter rateLimiter = RateLimiter.of(
            "authValidate",
            RateLimiterConfig.custom()
                    .limitForPeriod(50)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .timeoutDuration(Duration.ZERO)
                    .build()
    );

    public AuthServiceClientImpl(@Qualifier("authWebClient") WebClient authWebClient) {
        this.authWebClient = authWebClient;
    }

    @Override
    public Mono<TokenValidationResponseDto> validateToken(String token) {
        return authWebClient.post()
                .uri(SecurityPaths.INTERNAL_VALIDATE)
                .bodyValue(ValidateTokenRequestDto.builder().token(token).build())
                .retrieve()
                .bodyToMono(TokenValidationResponseDto.class)
                .timeout(REQUEST_TIMEOUT)
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorMap(error -> new AuthServiceUnavailableException(
                        ErrorMessages.AUTH_SERVICE_UNAVAILABLE,
                        error
                ));
    }
}
