package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.ExamException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes={ExamManagementController.class,ExamAttemptController.class,ExamMediaController.class,ScoreConversionController.class})
public class ExamExceptionHandler {
    @ExceptionHandler(ExamException.class) public ResponseEntity<?> business(ExamException e){return ResponseEntity.status(e.status).body(Map.of("code",e.code,"message",e.getMessage(),"traceId",UUID.randomUUID().toString(),"details",e.details));}
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) public ResponseEntity<?> invalid(Exception e){return business(ExamException.invalid("Dữ liệu gửi lên không đúng định dạng."));}
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<?> size(Exception e){return business(new ExamException(413,"MEDIA_TOO_LARGE","Media tối đa 20 MB."));}
}
