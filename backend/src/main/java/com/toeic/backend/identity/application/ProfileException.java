package com.toeic.backend.identity.application;

/** Lỗi nghiệp vụ hồ sơ; không chứa giá trị đầu vào nhạy cảm. */
public class ProfileException extends RuntimeException {
    private final String code;
    private final int status;

    private ProfileException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public int getStatus() { return status; }

    public static ProfileException validation() {
        return new ProfileException("VALIDATION_ERROR", "Du lieu gui len khong hop le.", 400);
    }

    public static ProfileException currentPasswordInvalid() {
        return new ProfileException("CURRENT_PASSWORD_INVALID", "Mat khau hien tai khong dung.", 422);
    }
}
