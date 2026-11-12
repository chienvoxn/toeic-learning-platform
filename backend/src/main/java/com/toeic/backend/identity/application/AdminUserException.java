package com.toeic.backend.identity.application;

/** Lỗi quản trị không chứa email, mật khẩu hoặc token trong thông báo. */
public class AdminUserException extends RuntimeException {
    private final int status;
    private final String code;
    private AdminUserException(int status, String code, String message) {
        super(message); this.status=status; this.code=code;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static AdminUserException invalid() { return new AdminUserException(400,"VALIDATION_ERROR","Dữ liệu quản trị không hợp lệ."); }
    public static AdminUserException notFound() { return new AdminUserException(404,"USER_NOT_FOUND","Không tìm thấy tài khoản thuộc phạm vi quản lý."); }
    public static AdminUserException transition() { return new AdminUserException(409,"USER_STATUS_CONFLICT","Chỉ khóa/mở khóa tài khoản ACTIVE hoặc LOCKED."); }
}
