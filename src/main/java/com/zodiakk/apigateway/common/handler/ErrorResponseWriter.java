package com.zodiakk.apigateway.common.handler;

import com.zodiakk.apigateway.common.dto.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

@Component
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper = new JsonMapper();

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.empty();
        }

        ApiErrorResponse body = ApiErrorResponse.builder()
                .valid(status == HttpStatus.UNAUTHORIZED ? Boolean.FALSE : null)
                .status(status.value())
                .code(code)
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = jsonMapper.writeValueAsBytes(body);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
