package com.zodiakk.apigateway.client.auth;

import com.zodiakk.apigateway.client.auth.dto.response.TokenValidationResponseDto;
import reactor.core.publisher.Mono;

public interface AuthServiceClient {

    Mono<TokenValidationResponseDto> validateToken(String token);
}