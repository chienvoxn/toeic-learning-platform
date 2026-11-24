package com.toeic.backend.listening.infrastructure;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Đọc media của chính owner; khóa media trước grant, ghi lại đúng schema V20. */
@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class MediaReuseGrantRepository {
    public record Media(UUID mediaId,String resourceType,String mimeType,Long fileSize,Integer durationSeconds,String visibility,OffsetDateTime createdAt) { }
    public record Grant(UUID userId,boolean active,OffsetDateTime grantedAt,OffsetDateTime revokedAt,boolean changed) { }
    public record Page<T>(List<T> content,long totalElements,int page,int size) { }
    private final JdbcTemplate jdbc;
    public MediaReuseGrantRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Page<Media> owned(UUID actor,int page,int size){
        var content=jdbc.query("SELECT resource_id,resource_type,mime_type,file_size,duration_seconds,visibility,created_at FROM media_resources WHERE owner_id=? ORDER BY created_at DESC,resource_id LIMIT ? OFFSET ?",
            (r,n)->new Media(r.getObject(1,UUID.class),r.getString(2),r.getString(3),(Long)r.getObject(4),(Integer)r.getObject(5),r.getString(6),r.getObject(7,OffsetDateTime.class)),actor,size,(long)page*size);
        return new Page<>(content,jdbc.queryForObject("SELECT count(*) FROM media_resources WHERE owner_id=?",Long.class,actor),page,size);
    }
    public boolean owner(UUID media,UUID actor,boolean lock){
        return !jdbc.query("SELECT resource_id FROM media_resources WHERE resource_id=? AND owner_id=?"+(lock?" FOR UPDATE":""),(r,n)->r.getObject(1,UUID.class),media,actor).isEmpty();
    }
    public boolean lockUsers(UUID actor,UUID recipient){
        // Lock FK users before media. Practice start also locks its actor before media.
        return jdbc.query("SELECT user_id FROM users WHERE user_id IN (?,?) ORDER BY user_id::text FOR KEY SHARE",(r,n)->r.getObject(1,UUID.class),actor,recipient).size()==2;
    }
    private org.springframework.jdbc.core.RowMapper<Grant> grantRow(){return (r,n)->new Grant(r.getObject("user_id",UUID.class),r.getObject("revoked_at")==null,r.getObject("granted_at",OffsetDateTime.class),r.getObject("revoked_at",OffsetDateTime.class),false);}
    public Optional<Grant> current(UUID media,UUID user){return jdbc.query("SELECT user_id,granted_at,revoked_at FROM media_reuse_grants WHERE media_id=? AND user_id=? FOR UPDATE",grantRow(),media,user).stream().findFirst();}
    public Page<Grant> grants(UUID media,int page,int size){
        return new Page<>(jdbc.query("SELECT user_id,granted_at,revoked_at FROM media_reuse_grants WHERE media_id=? ORDER BY user_id LIMIT ? OFFSET ?",grantRow(),media,size,(long)page*size),jdbc.queryForObject("SELECT count(*) FROM media_reuse_grants WHERE media_id=?",Long.class,media),page,size);
    }
    public void activate(UUID media,UUID user,UUID actor,OffsetDateTime now){
        jdbc.update("INSERT INTO media_reuse_grants(media_id,user_id,granted_by,granted_at) VALUES (?,?,?,?) ON CONFLICT(media_id,user_id) DO UPDATE SET granted_by=EXCLUDED.granted_by,granted_at=EXCLUDED.granted_at,revoked_by=NULL,revoked_at=NULL",media,user,actor,now);
    }
    public void revoke(UUID media,UUID user,UUID actor,OffsetDateTime now){jdbc.update("UPDATE media_reuse_grants SET revoked_by=?,revoked_at=? WHERE media_id=? AND user_id=?",actor,now,media,user);}
}
