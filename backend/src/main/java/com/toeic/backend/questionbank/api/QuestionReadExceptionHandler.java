package com.toeic.backend.questionbank.api;

import com.toeic.backend.questionbank.application.QuestionReadException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = QuestionReadController.class)
public class QuestionReadExceptionHandler {
    @ExceptionHandler(QuestionReadException.class)
    public ResponseEntity<Error> question(QuestionReadException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Error.of(exception.getCode(), exception.getMessage()));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Error> invalidInput(MethodArgumentTypeMismatchException exception) {
        return question(QuestionReadException.invalidQuery());
    }
    public record Error(String code, String message, String traceId, List<Object> details) {
        static Error of(String code, String message) {
            return new Error(code, message, UUID.randomUUID().toString(), List.of());
        }
    }
}
