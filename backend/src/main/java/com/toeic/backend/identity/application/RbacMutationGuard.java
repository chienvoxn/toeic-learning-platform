package com.toeic.backend.identity.application;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Một thứ tự khóa DB dùng chung: advisory RBAC → user (nếu có) → role → links. */
@Component
public class RbacMutationGuard {
    private static final long LOCK=73532002L;
    public static final Set<String> MANAGEMENT_PERMISSIONS=Set.of("rbac:manage","user:manage","user:read:self");
    private final JdbcTemplate jdbc;
    public RbacMutationGuard(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record State(UUID userId,boolean active,boolean admin,Set<String> permissions) {
        public State { permissions=Set.copyOf(permissions); }
        public boolean fullManagement() { return active && admin && permissions.containsAll(MANAGEMENT_PERMISSIONS); }
    }
    public State state(UUID id) {
        var rows=jdbc.query("""
            SELECT status,EXISTS(SELECT 1 FROM user_roles ur JOIN roles r USING(role_id) WHERE ur.user_id=u.user_id AND r.name='ADMIN') AS admin,
              ARRAY(SELECT DISTINCT p.code FROM user_roles ur JOIN role_permissions rp USING(role_id) JOIN permissions p USING(permission_id) WHERE ur.user_id=u.user_id) AS permissions
            FROM users u WHERE user_id=?
            """,(rs,n) -> new State(id,rs.getString("status").equals("ACTIVE"),rs.getBoolean("admin"),
                    new HashSet<>(Arrays.asList((String[])rs.getArray("permissions").getArray()))),id);
        if(rows.isEmpty())throw new AccessDeniedException("Actor unavailable");return rows.getFirst();
    }
    public State actor(String permission) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !(authentication.getPrincipal() instanceof SessionPrincipal principal))throw new AccessDeniedException("Session actor required");
        var actor=state(principal.userId());
        if(!actor.active() || !actor.admin() || !actor.permissions().contains(permission))throw new AccessDeniedException("Management permission required");
        return actor;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public State begin(String permission) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)",rs -> {},LOCK);
        // Re-read after acquiring the lock; do not trust authorities captured before waiting.
        return actor(permission);
    }
    public long activeManagers() {
        return jdbc.queryForObject("""
            SELECT count(*) FROM users u WHERE u.status='ACTIVE'
            AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r USING(role_id) WHERE ur.user_id=u.user_id AND r.name='ADMIN')
            AND 3=(SELECT count(DISTINCT p.code) FROM user_roles ur JOIN role_permissions rp USING(role_id) JOIN permissions p USING(permission_id)
                   WHERE ur.user_id=u.user_id AND p.code IN ('rbac:manage','user:manage','user:read:self'))
            """,Long.class);
    }
    public void finish(State before) {
        if(activeManagers()<1)throw RbacException.conflict("LAST_RBAC_ADMIN","Phải giữ ít nhất một Admin ACTIVE có đủ quyền quản trị RBAC.");
        var after=state(before.userId());
        if(!after.active() || !after.admin() || MANAGEMENT_PERMISSIONS.stream().anyMatch(p -> before.permissions().contains(p) && !after.permissions().contains(p)))
            throw RbacException.conflict("SELF_LOCKOUT","Không được tự làm mất khả năng quản trị của phiên đang dùng.");
        if(!before.permissions().containsAll(after.permissions()))throw RbacException.conflict("SELF_PRIVILEGE_ESCALATION","Không được tự cấp thêm quyền mà actor hiện không có.");
    }
}
