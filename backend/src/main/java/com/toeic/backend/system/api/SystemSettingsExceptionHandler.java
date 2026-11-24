package com.toeic.backend.system.api;

import com.toeic.backend.identity.api.AuthError;
import com.toeic.backend.system.application.*;
import com.toeic.backend.vocabulary.api.LearningProfileController;
import com.toeic.backend.vocabulary.api.VocabularyRecommendationController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={SystemSettingsController.class,LearningProfileController.class,VocabularyRecommendationController.class})
public class SystemSettingsExceptionHandler {
    @ExceptionHandler(SystemSettingsException.class) public ResponseEntity<AuthError> setting(SystemSettingsException exception){return ResponseEntity.status(exception.status()).body(AuthError.of(exception.code(),exception.getMessage()));}
    @ExceptionHandler(AdminReadException.class) public ResponseEntity<AuthError> query(AdminReadException exception){return ResponseEntity.status(exception.status()).body(AuthError.of(exception.code(),exception.getMessage()));}
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<AuthError> invalid(HttpMessageNotReadableException ignored){return setting(SystemSettingsException.invalid());}
    @ExceptionHandler(DataAccessException.class) public ResponseEntity<AuthError> unavailable(DataAccessException ignored){return setting(SystemSettingsException.unavailable());}
}
