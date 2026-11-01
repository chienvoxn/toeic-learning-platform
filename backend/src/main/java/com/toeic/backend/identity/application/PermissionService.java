package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.User;
import java.util.List;
import org.springframework.stereotype.Service;

/** Tổng hợp quyền hiện tại từ các role đã nạp trong transaction xác thực. */
@Service
public class PermissionService {
    public AccessRights resolve(User user) {
        var roles = user.getRoles();
        return new AccessRights(roles.stream().map(role -> role.getName()).sorted().toList(),
                roles.stream().flatMap(role -> role.getPermissions().stream())
                        .map(permission -> permission.getCode()).distinct().sorted().toList());
    }

    public record AccessRights(List<String> roles, List<String> permissions) {
        public AccessRights {
            roles = List.copyOf(roles);
            permissions = List.copyOf(permissions);
        }
    }
}
