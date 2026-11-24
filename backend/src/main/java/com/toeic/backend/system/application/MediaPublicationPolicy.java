package com.toeic.backend.system.application;

import com.toeic.backend.content.infrastructure.MediaReuseRepository;
import com.toeic.backend.identity.application.RbacMutationGuard.State;
import com.toeic.backend.system.infrastructure.ModerationRepository.Kind;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Tai su dung owner/grant va thu tu khoa cua authoring, khong doi publisher thanh author. */
@Component
public class MediaPublicationPolicy {
    private final MediaReuseRepository media;private final JdbcTemplate jdbc;
    public MediaPublicationPolicy(MediaReuseRepository media,JdbcTemplate jdbc){this.media=media;this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void validate(Kind kind,UUID version,State actor,String visibility){
        validateIds(ids(kind,version),actor,visibility,kind==Kind.contents);
    }
    public record Target(Kind kind,UUID version) { }
    /** Catalog chỉ kiểm khả dụng bằng read snapshot; start vẫn khóa và kiểm lại đúng M1. */
    @Transactional(propagation=Propagation.MANDATORY,readOnly=true)
    public boolean available(Kind kind,UUID version,State actor){
        for(var id:ids(kind,version)){
            var rows=jdbc.queryForList("SELECT owner_id,visibility,resource_type FROM media_resources WHERE resource_id=?",id);
            if(rows.isEmpty())return false;var r=rows.getFirst();
            boolean owned=actor.userId().equals(r.get("owner_id"));
            boolean granted=owned||Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM media_reuse_grants WHERE media_id=? AND user_id=? AND revoked_at IS NULL)",Boolean.class,id,actor.userId()));
            boolean readable="PUBLIC".equals(r.get("visibility"))||"AUTHENTICATED".equals(r.get("visibility"))&&actor.permissions().contains("content:read");
            if(!granted||!readable||kind!=Kind.contents&&!Set.of("AUDIO","IMAGE").contains(r.get("resource_type")))return false;
        }
        return true;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void validateBatch(List<Target> targets,State actor){
        // One global order across the whole aggregate, consistent with test publication.
        var ids=new TreeSet<UUID>(Comparator.comparing(UUID::toString));
        for(var target:targets)ids.addAll(ids(target.kind(),target.version()));
        validateIds(new ArrayList<>(ids),actor,null,false);
    }
    private List<UUID> ids(Kind kind,UUID version){
        String table=kind==Kind.contents?"learning_content_version_resources":kind==Kind.questions?"question_resources":"question_group_version_resources";
        var ids=kind==Kind.tests?jdbc.query("""
            SELECT resource_id FROM question_resources WHERE question_version_id IN (SELECT question_version_id FROM test_questions WHERE test_version_id=?)
            UNION SELECT resource_id FROM question_group_version_resources WHERE group_version_id IN (SELECT question_group_version_id FROM test_questions WHERE test_version_id=?)
            ORDER BY resource_id
            """,(r,n)->r.getObject(1,UUID.class),version,version):jdbc.query("SELECT resource_id FROM "+table+" WHERE "+kind.version+"=? ORDER BY resource_id",(r,n)->r.getObject(1,UUID.class),version);
        return ids;
    }
    private void validateIds(List<UUID> ids,State actor,String visibility,boolean content){
        var rows=new LinkedHashMap<UUID,MediaReuseRepository.Media>();
        for(var id:ids)rows.put(id,media.lockMedia(id).orElseThrow(()->ModerationException.conflict("PUBLICATION_MEDIA_UNAVAILABLE")));
        for(var entry:rows.entrySet()){
            var resource=entry.getValue();boolean owner=actor.userId().equals(resource.ownerId());
            boolean grant=!owner && media.lockActiveGrant(entry.getKey(),actor.userId());
            boolean readable="PUBLIC".equals(resource.visibility()) || "AUTHENTICATED".equals(resource.visibility()) && actor.permissions().contains("content:read");
            boolean type=content || Set.of("AUDIO","IMAGE").contains(jdbc.queryForObject("SELECT resource_type FROM media_resources WHERE resource_id=?",String.class,entry.getKey()));
            if(!(owner||grant)||!readable||!type||"PUBLIC".equals(visibility)&&!"PUBLIC".equals(resource.visibility()))throw ModerationException.conflict("PUBLICATION_MEDIA_UNAVAILABLE");
        }
    }
}
