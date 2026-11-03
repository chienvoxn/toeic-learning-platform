package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserProfile(UUID userId, String email, String fullName, String avatarUrl,
                          String status, OffsetDateTime emailVerifiedAt,
                          OffsetDateTime lastLoginAt, List<String> roles, Integer targetScore) {
    public static UserProfile from(User user, Integer targetScore) {
        return new UserProfile(user.getUserId(), user.getEmail(), user.getFullName(),
                user.getAvatarUrl(), user.getStatus(), user.getEmailVerifiedAt(),
                user.getLastLoginAt(), user.getRoles().stream().map(role -> role.getName()).sorted().toList(), targetScore);
    }
}
