package com.toeic.backend.identity.application;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminCapabilitiesTest {
    private PermissionService.AccessRights access(List<String> roles,String... permissions) {
        return new PermissionService.AccessRights(roles,List.of(permissions));
    }
    @Test void adminNameAloneOrPermissionAloneNeverGrantsUiManagement() {
        assertEquals(new AdminCapabilities(false,false,false,false,false),AdminCapabilities.from(access(List.of("ADMIN")),true));
        assertEquals(new AdminCapabilities(false,false,false,false,false),AdminCapabilities.from(access(List.of("LEARNER"),"user:manage","rbac:manage"),true));
    }
    @Test void capabilitiesFollowEffectivePermissionsAndActiveState() {
        var rights=access(List.of("ADMIN"),"user:manage","rbac:manage");
        assertEquals(new AdminCapabilities(true,true,false,false,false),AdminCapabilities.from(rights,true));
        assertEquals(new AdminCapabilities(false,false,false,false,false),AdminCapabilities.from(rights,false));
        assertEquals(new AdminCapabilities(true,false,false,false,false),AdminCapabilities.from(access(List.of("ADMIN"),"user:manage"),true));
        assertEquals(new AdminCapabilities(false,false,true,true,true),AdminCapabilities.from(access(List.of("ADMIN"),"audit:read","system:health:read","system:usage:read"),true));
    }
}
