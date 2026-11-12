package com.toeic.backend.identity.application;

/** Lỗi quản trị RBAC không chứa credential, token hoặc giá trị nhạy cảm. */
public class RbacException extends RuntimeException {
    private final int status;
    private final String code;
    public RbacException(int status,String code,String message) { super(message);this.status=status;this.code=code; }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static RbacException invalid() { return new RbacException(400,"VALIDATION_ERROR","Dữ liệu RBAC không hợp lệ."); }
    public static RbacException roleMissing() { return new RbacException(404,"ROLE_NOT_FOUND","Không tìm thấy vai trò."); }
    public static RbacException permissionMissing() { return new RbacException(404,"PERMISSION_NOT_FOUND","Không tìm thấy quyền được server hỗ trợ."); }
    public static RbacException userMissing() { return new RbacException(404,"USER_NOT_FOUND","Không tìm thấy tài khoản."); }
    public static RbacException conflict(String code,String message) { return new RbacException(409,code,message); }
}
