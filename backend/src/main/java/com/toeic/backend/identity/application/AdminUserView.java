package com.toeic.backend.identity.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.toeic.backend.identity.domain.User;

/** DTO allowlist cho quản trị; không trả credential, token hay dữ liệu học tập. */
public record AdminUserView(UUID userId,String email,String fullName,String status,List<String> roles,
                            OffsetDateTime createdAt,OffsetDateTime updatedAt) {
    public AdminUserView { roles=List.copyOf(roles); }
    public static AdminUserView from(User user) {
        return new AdminUserView(user.getUserId(),user.getEmail(),user.getFullName(),user.getStatus(),
                user.getRoles().stream().map(role -> role.getName()).sorted().toList(),user.getCreatedAt(),user.getUpdatedAt());
    }
    public record Page(List<AdminUserView> items,long totalElements,int page,int size) {
        public Page { items=List.copyOf(items); }
    }
}
