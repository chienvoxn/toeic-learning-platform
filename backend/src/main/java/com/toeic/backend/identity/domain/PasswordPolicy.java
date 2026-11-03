package com.toeic.backend.identity.domain;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static boolean fitsBcrypt(String value) {
        return value != null && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public static boolean validNewPassword(String value) {
        return value != null && !value.isBlank() && value.length() >= 8 && value.length() <= 72
                && fitsBcrypt(value);
    }
}
