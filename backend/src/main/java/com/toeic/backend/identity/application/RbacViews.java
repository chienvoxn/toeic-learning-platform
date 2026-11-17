package com.toeic.backend.identity.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** DTO an toàn dùng cho giao diện; cờ hệ thống/capability do server quyết định. */
public final class RbacViews {
    private RbacViews() {}
    public static final Set<String> SYSTEM_ROLES=Set.of("ADMIN","TEACHER","LEARNER");
    public record Permission(int permissionId,String code,String description) {}
    public record Role(int roleId,String name,String displayName,String description,List<Integer> permissionIds,
                       long userCount,boolean systemRole,boolean administrativeRole,boolean canRename,boolean canDelete) {
        public Role { permissionIds=List.copyOf(permissionIds); }
    }
    public record RoleRef(int roleId,String name,String displayName) {}
    public record UserRoles(UUID userId,String email,String fullName,String status,List<RoleRef> roles,boolean canChangeStatus) {
        public UserRoles { roles=List.copyOf(roles); }
    }
    public record Page<T>(List<T> items,long totalElements,int page,int size) {
        public Page { items=List.copyOf(items); }
    }
}
