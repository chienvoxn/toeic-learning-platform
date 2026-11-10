package com.toeic.backend.identity.application;

import org.springframework.http.HttpStatus;

public class GoogleAuthException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    public GoogleAuthException(String code, String message, HttpStatus status) {
        super(message); this.code = code; this.status = status;
    }
    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public static GoogleAuthException invalid() {
        return new GoogleAuthException("GOOGLE_CREDENTIAL_INVALID", "Thông tin xác thực Google không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.", HttpStatus.UNAUTHORIZED);
    }
    public static GoogleAuthException disabled() {
        return new GoogleAuthException("GOOGLE_NOT_CONFIGURED", "Đăng nhập Google chưa được cấu hình. Bạn vẫn có thể đăng nhập bằng email.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
