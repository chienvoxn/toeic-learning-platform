package com.toeic.backend.listening.api;

import com.toeic.backend.listening.application.ListeningMediaException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = {TeacherMediaController.class, AttemptListeningMediaController.class})
public class ListeningMediaExceptionHandler {
    @ExceptionHandler(ListeningMediaException.class)
    public ResponseEntity<Error> business(ListeningMediaException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new Error(ex.getCode(), ex.getMessage(), UUID.randomUUID().toString(), List.of()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MultipartException.class
    })
    public ResponseEntity<Error> invalid(Exception ex) {
        return business(ListeningMediaException.invalid());
    }

    public record Error(String code, String message, String traceId, List<Object> details) {
    }
}
