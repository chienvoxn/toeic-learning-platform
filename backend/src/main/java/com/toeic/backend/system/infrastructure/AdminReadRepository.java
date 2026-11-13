package com.toeic.backend.system.infrastructure;

import com.toeic.backend.system.application.*;
import com.toeic.backend.system.application.AdminReadViews.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.*;

/** Đọc audit có projection an toàn; data/count cùng predicate; thống kê aggregate ngay tại DB. */
@Repository
public class AdminReadRepository {
    public static final Set<String> ACTIONS=Set.of("USER_STATUS_CHANGED","ROLE_CREATED","ROLE_UPDATED","ROLE_DELETED","ROLE_PERMISSIONS_CHANGED","USER_ROLES_CHANGED","SYSTEM_SETTING_UPDATED");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AdminReadRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    private static final String ENTITY_ID="""
        coalesce(a.entity_id::text,CASE WHEN a.entity_type='ROLE' THEN
          CASE WHEN coalesce(a.details->'after'->>'roleId',a.details->'before'->>'roleId') ~ '^[1-9][0-9]{0,9}$'
          THEN CASE WHEN coalesce(a.details->'after'->>'roleId',a.details->'before'->>'roleId')::bigint<=2147483647
            THEN coalesce(a.details->'after'->>'roleId',a.details->'before'->>'roleId') END END
          WHEN a.entity_type='SYSTEM_SETTING' AND coalesce(a.details->'after'->>'key',a.details->'before'->>'key')
            IN ('vocabulary.recommendations.defaultLimit','learning.progress.defaultDays')
          THEN coalesce(a.details->'after'->>'key',a.details->'before'->>'key') END)
        """;
    private record Predicate(String sql,List<Object> args) {}
    private static Predicate predicate(AdminReadQuery.Audit q) {
        var sql=new StringBuilder(" FROM audit_logs a WHERE true");var args=new ArrayList<Object>();
        if(q.actor()!=null){sql.append(" AND a.actor_user_id=?");args.add(q.actor());}
        if("OTHER".equals(q.action()))sql.append(" AND a.action NOT IN ('USER_STATUS_CHANGED','ROLE_CREATED','ROLE_UPDATED','ROLE_DELETED','ROLE_PERMISSIONS_CHANGED','USER_ROLES_CHANGED','SYSTEM_SETTING_UPDATED')");
        else if(q.action()!=null){sql.append(" AND a.action=?");args.add(q.action());}
        if("OTHER".equals(q.entityType()))sql.append(" AND a.entity_type NOT IN ('USER','ROLE','SYSTEM_SETTING')");
        else if(q.entityType()!=null){sql.append(" AND a.entity_type=?");args.add(q.entityType());}
        if(q.entityId()!=null){sql.append(" AND (").append(ENTITY_ID).append(")=?");args.add(q.entityId());}
        if(q.window().from()!=null){sql.append(" AND a.created_at>=?");args.add(q.window().from());}
        if(q.window().to()!=null){sql.append(" AND a.created_at<?");args.add(q.window().to());}
        return new Predicate(sql.toString(),args);
    }
    private static String safeAction(String action) { return ACTIONS.contains(action)?action:"OTHER"; }
    private static String safeType(String type) { return Set.of("USER","ROLE","SYSTEM_SETTING").contains(type)?type:"OTHER"; }
    private static OffsetDateTime utc(OffsetDateTime value) { return value.withOffsetSameInstant(ZoneOffset.UTC); }
    private AuditItem item(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AuditItem(rs.getObject("audit_id",UUID.class),rs.getObject("actor_user_id",UUID.class),safeAction(rs.getString("action")),safeType(rs.getString("entity_type")),rs.getString("resource_id"),utc(rs.getObject("created_at",OffsetDateTime.class)));
    }
    private static final String SELECT="SELECT a.audit_id,a.actor_user_id,a.action,a.entity_type,a.created_at,"+ENTITY_ID+" AS resource_id";
    public Page<AuditItem> audit(AdminReadQuery.Audit q) {
        var p=predicate(q);long count=jdbc.queryForObject("SELECT count(*)"+p.sql(),Long.class,p.args().toArray());
        String column=q.sort().startsWith("action,")?"a.action":"a.created_at",direction=q.sort().endsWith(",asc")?" ASC":" DESC";
        var args=new ArrayList<>(p.args());args.add(q.size());args.add(q.offset());
        var items=jdbc.query(SELECT+p.sql()+" ORDER BY "+column+direction+",a.audit_id ASC LIMIT ? OFFSET ?",(rs,n) -> item(rs),args.toArray());
        return new Page<>(items,count,q.page(),q.size());
    }
    public AuditDetail audit(UUID id) {
        var rows=jdbc.query(SELECT+",a.details FROM audit_logs a WHERE a.audit_id=?",(rs,n) -> {
            var event=item(rs);var changes=new LinkedHashMap<String,Object>();
            // Never serialize arbitrary JSON, role names/descriptions, credentials, or PII.
            if(ACTIONS.contains(event.action()) && rs.getString("details")!=null) {
                JsonNode details=json.readTree(rs.getString("details"));
                if(event.action().equals("USER_STATUS_CHANGED")) {
                    for(String key:List.of("from","to")) {
                        var node=details.get(key);
                        if(node!=null && node.isString() && Set.of("ACTIVE","LOCKED","PENDING","DISABLED").contains(node.asString()))changes.put(key,node.asString());
                    }
                } else if(event.action().equals("USER_ROLES_CHANGED")) {
                    changes.put("beforeRoleIds",ids(details.path("before").path("roles"),true));
                    changes.put("afterRoleIds",ids(details.path("after").path("roles"),true));
                } else if(event.action().equals("SYSTEM_SETTING_UPDATED")) {
                    var definition=SettingDefinition.find(event.entityId());
                    if(definition.isPresent() && event.entityType().equals("SYSTEM_SETTING"))for(String side:List.of("before","after")) {
                        var value=details.path(side).path("value");var revision=details.path(side).path("revision");
                        if(value.isIntegralNumber() && value.canConvertToInt() && definition.get().accepts(value.asInt()))changes.put(side+"Value",value.asInt());
                        if(revision.isIntegralNumber() && revision.canConvertToLong() && revision.asLong()>=0)changes.put(side+"Revision",revision.asLong());
                    }
                } else {
                    changes.put("beforePermissionIds",ids(details.path("before").path("permissionIds"),false));
                    changes.put("afterPermissionIds",ids(details.path("after").path("permissionIds"),false));
                    var fields=new ArrayList<String>();
                    for(String field:List.of("name","displayName","description"))if(!details.path("before").path(field).equals(details.path("after").path(field)))fields.add(field);
                    changes.put("changedFields",fields);
                }
            }
            return new AuditDetail(event,changes);
        },id);
        return rows.stream().findFirst().orElseThrow(AdminReadException::missing);
    }
    private static List<Integer> ids(JsonNode values,boolean roles) {
        if(!values.isArray())return List.of();var ids=new TreeSet<Integer>();int limit=roles?100:1000,visited=0;
        for(var value:values) {
            if(visited++>=limit)break;var node=roles?value.path("roleId"):value;
            if(node.isIntegralNumber() && node.canConvertToInt() && node.asInt()>0)ids.add(node.asInt());
        }
        return List.copyOf(ids);
    }
    public Usage usage(AdminReadQuery.Window window,OffsetDateTime now) {
        long total=jdbc.queryForObject("SELECT count(*) FROM users",Long.class);
        long noRole=jdbc.queryForObject("SELECT count(*) FROM users u WHERE NOT EXISTS(SELECT 1 FROM user_roles ur WHERE ur.user_id=u.user_id)",Long.class);
        var status=jdbc.query("SELECT CASE WHEN status IN ('ACTIVE','LOCKED','PENDING','DISABLED') THEN status ELSE 'OTHER' END AS name,count(*) FROM users GROUP BY 1 ORDER BY 1",(rs,n) -> new Count(rs.getString(1),rs.getLong(2)));
        var roles=jdbc.query("SELECT r.role_id,r.name,count(DISTINCT ur.user_id) FROM roles r LEFT JOIN user_roles ur USING(role_id) GROUP BY r.role_id,r.name ORDER BY r.role_id",(rs,n) -> new RoleCount(rs.getInt(1),rs.getString(2).matches("[A-Z][A-Z0-9_]{1,49}")?rs.getString(2):"OTHER",rs.getLong(3)));
        var practice=attempts(window,"PRACTICE_SNAPSHOT");var section=attempts(window,"SECTION_SNAPSHOT");
        var audit=jdbc.query("SELECT action,count(*) FROM audit_logs WHERE created_at>=? AND created_at<? AND action IN ('USER_STATUS_CHANGED','ROLE_CREATED','ROLE_UPDATED','ROLE_DELETED','ROLE_PERMISSIONS_CHANGED','USER_ROLES_CHANGED','SYSTEM_SETTING_UPDATED') GROUP BY action ORDER BY action",(rs,n) -> new Count(rs.getString(1),rs.getLong(2)),window.from(),window.to());
        return new Usage(utc(now),window,new Users(total,noRole,status,roles),practice,section,new AuditActivity(audit.stream().mapToLong(Count::count).sum(),audit));
    }
    private Attempts attempts(AdminReadQuery.Window window,String source) {
        return jdbc.queryForObject("""
            SELECT count(*) FILTER (WHERE started_at>=? AND started_at<?),
              count(*) FILTER (WHERE submitted_at>=? AND submitted_at<? AND status IN ('SUBMITTED','GRADED'))
            FROM attempts WHERE attempt_source_type=?
            """,(rs,n) -> new Attempts(rs.getLong(1),rs.getLong(2)),window.from(),window.to(),window.from(),window.to(),source);
    }
}
