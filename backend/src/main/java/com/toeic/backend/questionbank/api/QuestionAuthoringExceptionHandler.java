package com.toeic.backend.questionbank.api;

import com.toeic.backend.questionbank.application.QuestionAuthoringException;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=TeacherQuestionController.class)
public class QuestionAuthoringExceptionHandler {
    @ExceptionHandler(QuestionAuthoringException.class)
    public ResponseEntity<Error> business(QuestionAuthoringException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new Error(exception.getCode(),exception.getMessage(),UUID.randomUUID().toString(),List.of()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Error> invalid(Exception exception) { return business(QuestionAuthoringException.invalid()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Error> reference(DataIntegrityViolationException exception) { return business(QuestionAuthoringException.conflict()); }
    public record Error(String code,String message,String traceId,List<Object> details) {}
}
