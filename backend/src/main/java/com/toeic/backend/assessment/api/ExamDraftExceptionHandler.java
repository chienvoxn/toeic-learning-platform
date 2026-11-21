package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.ExamDraftException;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=TeacherTestController.class)
public class ExamDraftExceptionHandler {
    @ExceptionHandler(ExamDraftException.class)
    public ResponseEntity<Error> business(ExamDraftException e) {
        return ResponseEntity.status(e.getStatus()).body(new Error(e.getCode(),e.getMessage(),UUID.randomUUID().toString(),List.of()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Error> invalid(Exception e) { return business(ExamDraftException.invalid()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Error> reference(DataIntegrityViolationException e) { return business(ExamDraftException.reference()); }
    public record Error(String code,String message,String traceId,List<Object> details) { }
}
