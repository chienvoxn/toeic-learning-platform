package com.toeic.backend.identity.application;

/** Cờ UI do server suy từ đúng role + permission đang thực thi tại API/service. */
public record AdminCapabilities(boolean userManagement,boolean rbacManagement) {
    public static AdminCapabilities from(PermissionService.AccessRights access,boolean active) {
        boolean admin=active && access.roles().contains("ADMIN");
        return new AdminCapabilities(admin && access.permissions().contains("user:manage"),
                admin && access.permissions().contains("rbac:manage"));
    }
}
