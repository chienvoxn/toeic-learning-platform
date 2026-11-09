package com.toeic.backend.questionbank.application;

public class QuestionAuthoringException extends RuntimeException {
    private final int status;
    private final String code;
    private QuestionAuthoringException(int status,String code,String message) { super(message); this.status=status; this.code=code; }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static QuestionAuthoringException invalid() { return new QuestionAuthoringException(400,"VALIDATION_ERROR","Du lieu khong hop le."); }
    public static QuestionAuthoringException missing() { return new QuestionAuthoringException(404,"QUESTION_AUTHORING_NOT_FOUND","Khong tim thay du lieu duoc phep quan ly."); }
    public static QuestionAuthoringException conflict() { return new QuestionAuthoringException(409,"QUESTION_STATE_CONFLICT","Trang thai hoac version khong cho phep thao tac."); }
    public static QuestionAuthoringException metadata() { return new QuestionAuthoringException(409,"QUESTION_METADATA_IMMUTABLE","Metadata identity chi sua khi DRAFT."); }
    public static QuestionAuthoringException mediaMissing() { return new QuestionAuthoringException(404,"MEDIA_NOT_FOUND","Khong tim thay media duoc phep su dung."); }
    public static QuestionAuthoringException mediaConflict() { return new QuestionAuthoringException(409,"QUESTION_MEDIA_REUSE_CONFLICT","Khong du quyen tai su dung media; yeu cau duoc rollback."); }
}
