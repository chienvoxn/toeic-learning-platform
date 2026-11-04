package com.toeic.backend.content.api;

import com.toeic.backend.content.application.ContentException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = {ContentController.class, PersonalContentController.class})
public class ContentExceptionHandler {
    @ExceptionHandler(ContentException.class)
    public ResponseEntity<Error> content(ContentException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Error.of(exception.getCode(), exception.getMessage()));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Error> invalidInput(Exception exception) {
        var invalid = ContentException.invalidQuery();
        return content(invalid);
    }
    public record Error(String code, String message, String traceId, List<Object> details) {
        static Error of(String code, String message) {
            return new Error(code, message, UUID.randomUUID().toString(), List.of());
        }
    }
}
