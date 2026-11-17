package com.toeic.backend.system.api;

import com.toeic.backend.identity.api.AuthError;
import com.toeic.backend.system.application.AdminReadException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=AdminMonitoringController.class)
public class AdminReadExceptionHandler {
    @ExceptionHandler(AdminReadException.class) public ResponseEntity<AuthError> business(AdminReadException exception) {return ResponseEntity.status(exception.status()).body(AuthError.of(exception.code(),exception.getMessage()));}
    @ExceptionHandler(DataAccessException.class) public ResponseEntity<AuthError> unavailable(DataAccessException ignored) {return ResponseEntity.status(503).body(AuthError.of("ADMIN_DATA_UNAVAILABLE","Chưa đọc được dữ liệu quản trị. Vui lòng thử lại."));}
}
