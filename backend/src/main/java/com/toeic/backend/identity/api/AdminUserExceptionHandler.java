package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.AdminUserException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=AdminUserController.class)
public class AdminUserExceptionHandler {
    @ExceptionHandler(com.toeic.backend.identity.application.RbacException.class)
    public ResponseEntity<AuthError> rbac(com.toeic.backend.identity.application.RbacException error) {
        return ResponseEntity.status(error.getStatus()).body(AuthError.of(error.getCode(),error.getMessage()));
    }
    @ExceptionHandler(AdminUserException.class) public ResponseEntity<AuthError> admin(AdminUserException error) {
        return ResponseEntity.status(error.getStatus()).body(AuthError.of(error.getCode(),error.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<AuthError> invalid(Exception ignored) {
        var error=AdminUserException.invalid(); return admin(error);
    }
}
