package com.toeic.backend.identity.application;

/** Chỉ chứa các trường người dùng được phép sửa; không nhận userId từ payload. */
public record ProfileUpdate(boolean fullNamePresent, String fullName,
                            boolean targetScorePresent, Integer targetScore) {
    public ProfileUpdate(String fullName) { this(true, fullName, false, null); }

    public ProfileUpdate {
        if ((!fullNamePresent && !targetScorePresent)
                || (!fullNamePresent && fullName != null)
                || (!targetScorePresent && targetScore != null)
                || (fullNamePresent && (fullName == null || fullName.isBlank() || fullName.length() > 150))
                || (targetScorePresent && targetScore != null
                    && (targetScore < 10 || targetScore > 990 || targetScore % 5 != 0))) {
            throw ProfileException.validation();
        }
        if (fullNamePresent) { fullName = fullName.strip(); }
    }
}
