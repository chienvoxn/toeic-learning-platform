package com.toeic.backend.assessment.application;

import java.util.List;

public class ExamException extends RuntimeException {
    public final int status;
    public final String code;
    public final List<Issue> details;
    public record Issue(String field, String reason) {}
    public ExamException(int status,String code,String message) { this(status,code,message,List.of()); }
    public ExamException(int status,String code,String message,List<Issue> details) {
        super(message); this.status=status; this.code=code; this.details=List.copyOf(details);
    }
    public static ExamException missing() { return new ExamException(404,"EXAM_NOT_FOUND","Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."); }
    public static ExamException conflict(String message) { return new ExamException(409,"EXAM_CONFLICT",message); }
    public static ExamException invalid(String message) { return new ExamException(400,"VALIDATION_ERROR",message); }
}
