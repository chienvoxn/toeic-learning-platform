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
    public static PracticeException notEditable() { return new PracticeException(409,"ATTEMPT_NOT_EDITABLE","Luot luyen tap khong con cho phep ghi dap an."); }
    public static PracticeException expired() { return new PracticeException(409,"ATTEMPT_EXPIRED","Luot luyen tap da het han."); }
    public static PracticeException questionMissing() { return new PracticeException(404,"ATTEMPT_QUESTION_NOT_FOUND","Khong tim thay cau hoi trong luot luyen tap."); }
    public static PracticeException choiceMismatch() { return new PracticeException(422,"CHOICE_VERSION_MISMATCH","Lua chon khong thuoc phien ban cau hoi da pin."); }
    public static PracticeException resultNotReady() { return new PracticeException(409,"RESULT_NOT_READY","Luot luyen tap chua duoc cham diem."); }
}
