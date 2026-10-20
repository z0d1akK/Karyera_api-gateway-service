package com.zodiakk.apigateway.common.handler;

import com.zodiakk.apigateway.common.exception.ErrorMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);

        return errorResponseWriter.write(
                exchange,
                status,
                resolveCode(status),
                resolveMessage(status)
        );
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            HttpStatus status = HttpStatus.resolve(responseStatusException.getStatusCode().value());

            if (status != null) {
                return status;
            }
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveCode(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case NOT_FOUND -> "ROUTE_NOT_FOUND";
            case SERVICE_UNAVAILABLE, BAD_GATEWAY -> "SERVICE_UNAVAILABLE";
            default -> "INTERNAL_SERVER_ERROR";
        };
    }

    private String resolveMessage(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> ErrorMessages.UNAUTHORIZED;
            case NOT_FOUND -> ErrorMessages.ROUTE_NOT_FOUND;
            case SERVICE_UNAVAILABLE, BAD_GATEWAY -> ErrorMessages.SERVICE_UNAVAILABLE;
            default -> ErrorMessages.INTERNAL_SERVER_ERROR;
        };
    }
}
