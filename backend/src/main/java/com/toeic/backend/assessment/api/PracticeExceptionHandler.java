package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.PracticeException;
import com.toeic.backend.questionbank.application.PracticeQuestionCatalog;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=PracticeAttemptController.class)
public class PracticeExceptionHandler {
    @ExceptionHandler(PracticeException.class)
    public ResponseEntity<Error> business(PracticeException ex) { return error(ex.getStatus(),ex.getCode(),ex.getMessage()); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Error> invalid(Exception ex) { return business(PracticeException.invalid()); }
    @ExceptionHandler(PracticeQuestionCatalog.Unavailable.class)
    public ResponseEntity<Error> unavailable(PracticeQuestionCatalog.Unavailable ex) {
        return ex.sourceChanged()?error(409,"PRACTICE_SOURCE_CHANGED","Nguon cau hoi da thay doi; vui long thu lai.")
                :error(409,"PRACTICE_SNAPSHOT_UNAVAILABLE","Snapshot khong con kha dung; khong thay the cau hoi.");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Error> integrity(DataIntegrityViolationException ex) { return error(409,"PRACTICE_SOURCE_CHANGED","Nguon cau hoi da thay doi; vui long thu lai."); }
    private ResponseEntity<Error> error(int status,String code,String message) {
        return ResponseEntity.status(status).body(new Error(code,message,UUID.randomUUID().toString(),List.of()));
    }
    public record Error(String code,String message,String traceId,List<Object> details) { }
}
