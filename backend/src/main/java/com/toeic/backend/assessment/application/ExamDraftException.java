package com.toeic.backend.assessment.application;

public class ExamDraftException extends RuntimeException {
    private final int status;
    private final String code;
    private ExamDraftException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static ExamDraftException invalid() { return new ExamDraftException(400,"VALIDATION_ERROR","Dữ liệu bộ đề không hợp lệ."); }
    public static ExamDraftException missing() { return new ExamDraftException(404,"TEST_NOT_FOUND","Không tìm thấy bộ đề hoặc phiên bản."); }
    public static ExamDraftException stale() { return new ExamDraftException(409,"TEST_REVISION_CONFLICT","Bộ đề đã thay đổi; đọc lại trước khi lưu."); }
    public static ExamDraftException immutable() { return new ExamDraftException(409,"TEST_VERSION_NOT_EDITABLE","Chỉ sửa DRAFT chưa được attempt tham chiếu."); }
    public static ExamDraftException reference() { return new ExamDraftException(422,"TEST_REFERENCE_INVALID","Tham chiếu phải là version published hiện hành, đúng Part và nguyên nhóm theo thứ tự."); }
    public static ExamDraftException conflict(String code) { return new ExamDraftException(409,code,"Trạng thái, blueprint hoặc dữ liệu bộ đề chưa đủ điều kiện cho thao tác này."); }
}
