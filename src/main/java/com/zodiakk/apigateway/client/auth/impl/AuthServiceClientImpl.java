package com.zodiakk.apigateway.client.auth.impl;

import com.zodiakk.apigateway.client.auth.AuthServiceClient;
import com.zodiakk.apigateway.client.auth.dto.request.ValidateTokenRequestDto;
import com.zodiakk.apigateway.client.auth.dto.response.TokenValidationResponseDto;
import com.zodiakk.apigateway.common.constants.SecurityPaths;
import com.zodiakk.apigateway.common.exception.AuthServiceUnavailableException;
import com.zodiakk.apigateway.common.exception.ErrorMessages;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class AuthServiceClientImpl implements AuthServiceClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient authWebClient;

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
                .onErrorMap(error -> new AuthServiceUnavailableException(
                        ErrorMessages.AUTH_SERVICE_UNAVAILABLE,
                        error
                ));
    }
}
