package com.toeic.backend.system.api;

import com.toeic.backend.identity.api.AuthError;
import com.toeic.backend.system.application.ModerationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=ModerationController.class)
public class ModerationExceptionHandler {
    @ExceptionHandler(ModerationException.class) public ResponseEntity<AuthError> conflict(ModerationException e){return ResponseEntity.status(e.status()).body(AuthError.of(e.code(),e.getMessage()));}
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<AuthError> invalid(HttpMessageNotReadableException e){return conflict(ModerationException.invalid());}
    @ExceptionHandler(DataAccessException.class) public ResponseEntity<AuthError> unavailable(DataAccessException e){return ResponseEntity.status(503).body(AuthError.of("MODERATION_UNAVAILABLE","Chua luu duoc transition. Vui long thu lai."));}
}
