package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.application.AdminUserQuery;
import com.toeic.backend.identity.application.AdminUserView;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc data/count cùng phạm vi DB; audit dùng transaction của service. */
@Repository
public class AdminUserRepository {
    private final JdbcTemplate jdbc;
    public AdminUserRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String SCOPE="""
        FROM users u WHERE
        EXISTS(SELECT 1 FROM user_roles ur JOIN roles r USING(role_id) WHERE ur.user_id=u.user_id AND r.name IN ('LEARNER','TEACHER'))
        AND NOT EXISTS(SELECT 1 FROM user_roles ur JOIN roles r USING(role_id) WHERE ur.user_id=u.user_id AND r.name='ADMIN')
        AND (strpos(lower(u.full_name COLLATE "und-x-icu"),lower(? COLLATE "und-x-icu"))>0
          OR strpos(lower(u.email COLLATE "und-x-icu"),lower(? COLLATE "und-x-icu"))>0)
        AND (? IS NULL OR EXISTS(SELECT 1 FROM user_roles ur JOIN roles r USING(role_id) WHERE ur.user_id=u.user_id AND r.name=?))
        AND (? IS NULL OR u.status=?)
        """;
    public AdminUserView.Page search(AdminUserQuery query) {
        Object[] args={query.q(),query.q(),query.role(),query.role(),query.status(),query.status()};
        // PostgreSQL needs a typed nullable parameter independently of the second occurrence.
        String scope=SCOPE.replace("? IS NULL","CAST(? AS text) IS NULL");
        long total=jdbc.queryForObject("SELECT count(*) "+scope,Long.class,args);
        String column=switch(query.sort().split(",")[0]) { case "fullName" -> "u.full_name"; case "email" -> "u.email"; default -> "u.created_at"; };
        String direction=query.sort().endsWith(",asc")?" ASC":" DESC";
        Object[] paged=Arrays.copyOf(args,8); paged[6]=query.size(); paged[7]=(long)query.page()*query.size();
        var items=jdbc.query("""
            SELECT u.user_id,u.email,u.full_name,u.status,u.created_at,u.updated_at,
              ARRAY(SELECT r.name FROM user_roles ur JOIN roles r USING(role_id) WHERE ur.user_id=u.user_id ORDER BY r.name) AS roles
            """+scope+" ORDER BY "+column+direction+",u.user_id ASC LIMIT ? OFFSET ?",(rs,n) -> new AdminUserView(
                rs.getObject("user_id",UUID.class),rs.getString("email"),rs.getString("full_name"),rs.getString("status"),
                Arrays.asList((String[])rs.getArray("roles").getArray()),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class)),paged);
        return new AdminUserView.Page(items,total,query.page(),query.size());
    }
    public void audit(UUID actor,UUID target,String previous,String next,OffsetDateTime now) {
        jdbc.update("""
            INSERT INTO audit_logs(audit_id,actor_user_id,action,entity_type,entity_id,details,created_at)
            VALUES (?,?,'USER_STATUS_CHANGED','USER',?,jsonb_build_object('from',CAST(? AS text),'to',CAST(? AS text)),?)
            """,UUID.randomUUID(),actor,target,previous,next,now);
    }
}
