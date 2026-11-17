package com.toeic.backend.system.application;

public class AdminReadException extends RuntimeException {
    private final int status;
    private final String code;
    public AdminReadException(int status,String code,String message) { super(message);this.status=status;this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static AdminReadException invalid() { return new AdminReadException(400,"VALIDATION_ERROR","Bộ lọc quản trị không hợp lệ."); }
    public static AdminReadException missing() { return new AdminReadException(404,"AUDIT_NOT_FOUND","Không tìm thấy bản ghi audit."); }
}
