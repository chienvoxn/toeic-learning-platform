package com.toeic.backend.identity.application;

/** Cờ UI do server suy từ đúng role + permission đang thực thi tại API/service. */
public record AdminCapabilities(boolean userManagement,boolean rbacManagement,boolean auditRead,boolean healthRead,boolean usageRead,boolean settingsManagement,boolean contentManagement,boolean questionManagement) {
    public static AdminCapabilities from(PermissionService.AccessRights access,boolean active) {
        boolean admin=active && access.roles().contains("ADMIN");
        return new AdminCapabilities(admin && access.permissions().contains("user:manage"),
                admin && access.permissions().contains("rbac:manage"),admin && access.permissions().contains("audit:read"),
                admin && access.permissions().contains("system:health:read"),admin && access.permissions().contains("system:usage:read"),
                admin && access.permissions().contains("system:settings:manage"),
                admin && access.permissions().contains("content:manage:any"),admin && access.permissions().contains("question:manage:any"));
    }
}
