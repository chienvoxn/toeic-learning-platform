package com.toeic.backend.system.api;

import com.toeic.backend.content.api.TeacherContentController;
import com.toeic.backend.questionbank.api.TeacherQuestionController;
import com.toeic.backend.identity.api.AuthError;
import com.toeic.backend.system.application.ContentManagementAudit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={TeacherContentController.class,TeacherQuestionController.class})
public class ContentManagementExceptionHandler {
    @ExceptionHandler(ContentManagementAudit.Unavailable.class)
    public ResponseEntity<AuthError> unavailable(ContentManagementAudit.Unavailable failure) {
        boolean archive=failure.operation()==ContentManagementAudit.Operation.ARCHIVED;
        return ResponseEntity.status(503).body(AuthError.of(archive?"ARCHIVE_AUDIT_UNAVAILABLE":"CONTENT_AUDIT_UNAVAILABLE",
                archive?"Chưa lưu được thao tác archive. Vui lòng thử lại.":"Chưa lưu được thay đổi nội dung. Vui lòng thử lại."));
    }
}
