package com.zodiakk.apigateway.common.exception;

public class AuthServiceUnavailableException extends RuntimeException {

    public AuthServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
