package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.AuthException;
import com.toeic.backend.identity.application.ProfileException;
import com.toeic.backend.identity.application.GoogleAuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AuthController.class, UserController.class, GoogleAuthController.class})
public class AuthExceptionHandler {
    @ExceptionHandler(GoogleAuthException.class)
    public ResponseEntity<AuthError> google(GoogleAuthException exception) {
        return ResponseEntity.status(exception.getStatus()).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(AuthError.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<AuthError> identityConflict(org.springframework.dao.DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(AuthError.of("IDENTITY_CONFLICT", "Tài khoản vừa được cập nhật. Vui lòng thử đăng nhập lại."));
    }
    @ExceptionHandler(ProfileException.class)
    public ResponseEntity<AuthError> profile(ProfileException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(AuthError.of(exception.getCode(), exception.getMessage()));
    }

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
