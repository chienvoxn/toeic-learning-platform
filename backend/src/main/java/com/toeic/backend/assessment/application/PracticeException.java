package com.toeic.backend.assessment.application;

public class PracticeException extends RuntimeException {
    private final int status;
    private final String code;
    private PracticeException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static PracticeException invalid() { return new PracticeException(400,"VALIDATION_ERROR","Du lieu luyen tap khong hop le."); }
    public static PracticeException missing() { return new PracticeException(404,"ATTEMPT_NOT_FOUND","Khong tim thay luot luyen tap."); }
    public static PracticeException insufficient() { return new PracticeException(409,"PRACTICE_QUESTIONS_INSUFFICIENT","Khong du bo cau hoi phu hop."); }
}
