package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.RbacException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=AdminRbacController.class)
public class RbacExceptionHandler {
    @ExceptionHandler(RbacException.class) public ResponseEntity<AuthError> business(RbacException error) {
        return ResponseEntity.status(error.getStatus()).body(AuthError.of(error.getCode(),error.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) public ResponseEntity<AuthError> invalid(Exception ignored) { return business(RbacException.invalid()); }
    @ExceptionHandler(DuplicateKeyException.class) public ResponseEntity<AuthError> duplicate(DuplicateKeyException ignored) { return business(RbacException.conflict("ROLE_CODE_CONFLICT","Mã vai trò đã tồn tại.")); }
    @ExceptionHandler(DataAccessException.class) public ResponseEntity<AuthError> persistence(DataAccessException ignored) {
        return ResponseEntity.internalServerError().body(AuthError.of("RBAC_PERSISTENCE_ERROR","Không lưu được RBAC; thay đổi đã được hoàn tác."));
    }
}
