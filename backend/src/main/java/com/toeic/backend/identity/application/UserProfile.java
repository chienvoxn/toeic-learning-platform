package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserProfile(UUID userId, String email, String fullName, String avatarUrl,
                          String status, OffsetDateTime emailVerifiedAt,
                          OffsetDateTime lastLoginAt, List<String> roles, Integer targetScore,
                          boolean localLoginEnabled, AdminCapabilities adminCapabilities) {
    public static UserProfile from(User user, Integer targetScore) {
        var access = PermissionService.resolveSnapshot(user);
        return new UserProfile(user.getUserId(), user.getEmail(), user.getFullName(),
                user.getAvatarUrl(), user.getStatus(), user.getEmailVerifiedAt(),
                user.getLastLoginAt(), access.roles(), targetScore,
                user.getPasswordHash() != null, AdminCapabilities.from(access, "ACTIVE".equals(user.getStatus())));
    }
}
