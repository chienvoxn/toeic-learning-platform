package com.toeic.backend.identity.application;

import org.springframework.http.HttpStatus;

public class OtpRejectedException extends RegistrationException {

    public OtpRejectedException(
            String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}