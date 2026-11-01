package com.toeic.backend.identity.application;

import org.springframework.security.core.AuthenticationException;

public class AuthException extends AuthenticationException {
    private final String code;

    public AuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }

    public static AuthException invalidCredentials() {
        return new AuthException("INVALID_CREDENTIALS", "Email hoac mat khau khong hop le, hoac tai khoan chua san sang.");
    }

    public static AuthException invalidToken() {
        return new AuthException("INVALID_TOKEN", "Phien dang nhap khong hop le hoac da bi thu hoi.");
    }
}
