package com.toeic.backend.identity.application;

import org.springframework.http.HttpStatus;

public class RegistrationException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public RegistrationException(
            String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}