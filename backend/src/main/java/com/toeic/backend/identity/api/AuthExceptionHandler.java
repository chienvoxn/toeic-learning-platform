package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AuthController.class, UserController.class})
public class AuthExceptionHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<AuthError> authentication(AuthException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Bearer")
                .body(AuthError.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<AuthError> validation(Exception exception) {
        return ResponseEntity.badRequest().body(AuthError.of("VALIDATION_ERROR", "Du lieu gui len khong hop le."));
    }
}
