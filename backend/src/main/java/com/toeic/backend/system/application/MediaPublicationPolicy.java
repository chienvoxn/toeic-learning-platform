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
        String table=kind==Kind.contents?"learning_content_version_resources":kind==Kind.questions?"question_resources":"question_group_version_resources";
        var ids=jdbc.query("SELECT resource_id FROM "+table+" WHERE "+kind.version+"=? ORDER BY resource_id",(r,n)->r.getObject(1,UUID.class),version);
        var rows=new LinkedHashMap<UUID,MediaReuseRepository.Media>();
        for(var id:ids)rows.put(id,media.lockMedia(id).orElseThrow(()->ModerationException.conflict("PUBLICATION_MEDIA_UNAVAILABLE")));
        for(var entry:rows.entrySet()){
            var resource=entry.getValue();boolean owner=actor.userId().equals(resource.ownerId());
            boolean grant=!owner && media.lockActiveGrant(entry.getKey(),actor.userId());
            boolean readable="PUBLIC".equals(resource.visibility()) || "AUTHENTICATED".equals(resource.visibility()) && actor.permissions().contains("content:read");
            boolean type=kind==Kind.contents || Set.of("AUDIO","IMAGE").contains(jdbc.queryForObject("SELECT resource_type FROM media_resources WHERE resource_id=?",String.class,entry.getKey()));
            if(!(owner||grant)||!readable||!type||"PUBLIC".equals(visibility)&&!"PUBLIC".equals(resource.visibility()))throw ModerationException.conflict("PUBLICATION_MEDIA_UNAVAILABLE");
        }
    }
}
