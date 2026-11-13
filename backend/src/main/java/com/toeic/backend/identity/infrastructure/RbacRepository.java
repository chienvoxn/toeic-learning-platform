package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.application.*;
import com.toeic.backend.identity.application.RbacViews.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Persistence RBAC hiện có; chỉ ghi các bảng liên kết xác định, không cascade dữ liệu. */
@Repository
public class RbacRepository {
    // Only codes with enforcement in the existing server; no arbitrary permission CRUD.
    public static final Set<String> ENFORCED_PERMISSIONS=Set.of("user:read:self","user:update:self","user:password:change:self",
            "user:manage","rbac:manage","content:read","content:bookmark:self","content:history:self","content:manage:self","content:manage:any",
            "question:read","question:manage:self","question:manage:any","practice:manage:self","exam:manage:self",
            "audit:read","system:health:read","system:usage:read");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public RbacRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    private static final String ROLE_SELECT="""
        SELECT r.role_id,r.name,coalesce(r.display_name,r.name) AS display_name,r.description,
          ARRAY(SELECT permission_id FROM role_permissions rp WHERE rp.role_id=r.role_id ORDER BY permission_id) AS permission_ids,
          (SELECT count(*) FROM user_roles ur WHERE ur.role_id=r.role_id) AS user_count FROM roles r
        """;
    private static final RowMapper<Role> ROLE_ROW=(rs,n) -> {
        String code=rs.getString("name");boolean system=RbacViews.SYSTEM_ROLES.contains(code);long users=rs.getLong("user_count");
        return new Role(rs.getInt("role_id"),code,rs.getString("display_name"),rs.getString("description"),
                Arrays.asList((Integer[])rs.getArray("permission_ids").getArray()),users,system,code.equals("ADMIN"),!system,!system && users==0);
    };
    private static final String ROLE_SEARCH="""
        WHERE strpos(lower(r.name COLLATE "und-x-icu"),lower(? COLLATE "und-x-icu"))>0
          OR strpos(lower(coalesce(r.display_name,r.name) COLLATE "und-x-icu"),lower(? COLLATE "und-x-icu"))>0
        """;
    public Page<Role> roles(RbacQuery query) {
        long count=jdbc.queryForObject("SELECT count(*) FROM roles r "+ROLE_SEARCH,Long.class,query.q(),query.q());
        var rows=jdbc.query(ROLE_SELECT+ROLE_SEARCH+" ORDER BY r.name,r.role_id LIMIT ? OFFSET ?",ROLE_ROW,query.q(),query.q(),query.size(),query.offset());
        return new Page<>(rows,count,query.page(),query.size());
    }
    public Role role(int id) {
        return jdbc.query(ROLE_SELECT+" WHERE r.role_id=?",ROLE_ROW,id).stream().findFirst().orElseThrow(RbacException::roleMissing);
    }
    public List<Permission> permissions() {
        return jdbc.query("SELECT permission_id,code,description FROM permissions ORDER BY code",(rs,n) ->
                new Permission(rs.getInt(1),rs.getString(2),rs.getString(3))).stream().filter(p -> ENFORCED_PERMISSIONS.contains(p.code())).toList();
    }
    public void lockRole(int id) {
        if(jdbc.queryForList("SELECT role_id FROM roles WHERE role_id=? FOR UPDATE",id).isEmpty())throw RbacException.roleMissing();
    }
    public void lockUser(UUID id) {
        if(jdbc.queryForList("SELECT user_id FROM users WHERE user_id=? FOR UPDATE",id).isEmpty())throw RbacException.userMissing();
    }
    public boolean duplicateCode(String code,int except) {
        return jdbc.queryForObject("SELECT count(*)>0 FROM roles WHERE lower(name)=lower(?) AND role_id<>?",Boolean.class,code,except);
    }
    public int create(RoleEdit edit) {
        return jdbc.queryForObject("INSERT INTO roles(name,display_name,description) VALUES (?,?,?) RETURNING role_id",Integer.class,
                edit.name(),edit.displayNamePresent()?edit.displayName():null,edit.descriptionPresent()?edit.description():null);
    }
    public void edit(int id,RoleEdit edit) {
        if(edit.namePresent())jdbc.update("UPDATE roles SET name=? WHERE role_id=?",edit.name(),id);
        if(edit.displayNamePresent())jdbc.update("UPDATE roles SET display_name=? WHERE role_id=?",edit.displayName(),id);
        if(edit.descriptionPresent())jdbc.update("UPDATE roles SET description=? WHERE role_id=?",edit.description(),id);
    }
    public void delete(int id) {
        jdbc.update("DELETE FROM role_permissions WHERE role_id=?",id);
        jdbc.update("DELETE FROM roles WHERE role_id=?",id);
    }
    public void permissions(int role,List<Integer> ids) {
        jdbc.update("DELETE FROM role_permissions WHERE role_id=?",role);
        for(int id:ids)jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)",role,id);
    }
    public void userRoles(UUID user,List<Integer> ids,OffsetDateTime now) {
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",user);
        for(int id:ids)jdbc.update("INSERT INTO user_roles(user_id,role_id,assigned_at) VALUES (?,?,?)",user,id,now);
    }
    private static final String USER_SEARCH="""
        WHERE strpos(lower(u.email COLLATE "und-x-icu"),lower(? COLLATE "und-x-icu"))>0
          OR strpos(lower(u.full_name COLLATE "und-x-icu"),lower(? COLLATE "und-x-icu"))>0
        """;
    public Page<UserRoles> users(RbacQuery query,UUID actor,boolean statusAllowed) {
        long count=jdbc.queryForObject("SELECT count(*) FROM users u "+USER_SEARCH,Long.class,query.q(),query.q());
        var ids=jdbc.query("SELECT u.user_id FROM users u "+USER_SEARCH+" ORDER BY u.email,u.user_id LIMIT ? OFFSET ?",(rs,n) -> rs.getObject(1,UUID.class),query.q(),query.q(),query.size(),query.offset());
        return new Page<>(ids.stream().map(id -> user(id,actor,statusAllowed)).toList(),count,query.page(),query.size());
    }
    public UserRoles user(UUID id,UUID actor,boolean statusAllowed) {
        var rows=jdbc.query("SELECT user_id,email,full_name,status FROM users WHERE user_id=?",(rs,n) -> new UserRoles(
                id,rs.getString("email"),rs.getString("full_name"),rs.getString("status"),List.of(),false),id);
        if(rows.isEmpty())throw RbacException.userMissing();
        var row=rows.getFirst();
        var roles=jdbc.query("SELECT r.role_id,r.name,coalesce(r.display_name,r.name) FROM user_roles ur JOIN roles r USING(role_id) WHERE ur.user_id=? ORDER BY r.role_id",
                (rs,n) -> new RoleRef(rs.getInt(1),rs.getString(2),rs.getString(3)),id);
        boolean managedAccount=roles.stream().anyMatch(role -> RbacViews.SYSTEM_ROLES.contains(role.name()));
        return new UserRoles(id,row.email(),row.fullName(),row.status(),roles,statusAllowed && managedAccount && !id.equals(actor) && Set.of("ACTIVE","LOCKED").contains(row.status()));
    }
    public void audit(UUID actor,String action,String type,UUID target,Object before,Object after,OffsetDateTime now) {
        var details=new LinkedHashMap<String,Object>();details.put("before",before);details.put("after",after);
        jdbc.update("INSERT INTO audit_logs(audit_id,actor_user_id,action,entity_type,entity_id,details,created_at) VALUES (?,?,?,?,?,CAST(? AS jsonb),?)",
                UUID.randomUUID(),actor,action,type,target,json.writeValueAsString(details),now);
    }
}
