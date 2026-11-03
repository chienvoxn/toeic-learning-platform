package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.PasswordPolicy;

public record PasswordChange(String currentPassword, String newPassword) {
    public PasswordChange {
        if (currentPassword == null || currentPassword.isBlank() || currentPassword.length() > 72
                || !PasswordPolicy.fitsBcrypt(currentPassword) || !PasswordPolicy.validNewPassword(newPassword)) {
            throw ProfileException.validation();
        }
    }

    @Override
    public String toString() { return "PasswordChange[redacted]"; }
}
