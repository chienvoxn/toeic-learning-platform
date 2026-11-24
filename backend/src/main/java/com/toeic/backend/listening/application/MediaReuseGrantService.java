package com.toeic.backend.listening.application;

import com.toeic.backend.identity.application.RbacMutationGuard;
import com.toeic.backend.identity.infrastructure.RbacRepository;
import com.toeic.backend.listening.infrastructure.MediaReuseGrantRepository;
import com.toeic.backend.listening.infrastructure.MediaReuseGrantRepository.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Owner cấp/thu hồi reuse nguyên tử với audit; không thay owner, visibility hoặc quyền publish. */
@Service
@PreAuthorize("hasAnyAuthority('question:manage:self','question:manage:any','content:manage:self','content:manage:any') and @questionAuthoringAccess.isActor(#actor)")
public class MediaReuseGrantService {
    private static final Set<String> RIGHTS=Set.of("question:manage:self","question:manage:any","content:manage:self","content:manage:any");
    private final MediaReuseGrantRepository data;private final RbacMutationGuard rights;private final RbacRepository audit;private final JdbcTemplate jdbc;private final Clock clock;
    public MediaReuseGrantService(MediaReuseGrantRepository data,RbacMutationGuard rights,RbacRepository audit,JdbcTemplate jdbc,Clock clock){this.data=data;this.rights=rights;this.audit=audit;this.jdbc=jdbc;this.clock=clock;}
    private void allowed(UUID actor){var state=rights.state(actor);if(!state.active()||Collections.disjoint(state.permissions(),RIGHTS))throw new AccessDeniedException("Media management permission required");}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Media> owned(UUID actor,int page,int size){allowed(actor);return data.owned(actor,page,size);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Grant> grants(UUID actor,UUID media,int page,int size){allowed(actor);if(!data.owner(media,actor,false))throw ListeningMediaException.mediaNotFound();return data.grants(media,page,size);}
    @Transactional
    public Grant change(UUID actor,UUID media,UUID recipient,boolean active){
        if(actor==null||media==null||recipient==null||actor.equals(recipient))throw ListeningMediaException.invalid();
        jdbc.query("SELECT pg_advisory_xact_lock_shared(73532002)",r->{});allowed(actor);
        boolean usersExist=data.lockUsers(actor,recipient);
        if(!data.owner(media,actor,true))throw ListeningMediaException.mediaNotFound();
        if(!usersExist)throw new ListeningMediaException(404,"MEDIA_GRANTEE_NOT_FOUND","Không tìm thấy người nhận quyền.");
        var old=data.current(media,recipient).orElse(null);boolean wasActive=old!=null&&old.active();
        if(active==wasActive)return old==null?new Grant(recipient,false,null,null,false):old;
        var now=OffsetDateTime.now(clock);
        if(active)data.activate(media,recipient,actor,now);else data.revoke(media,recipient,actor,now);
        var value=data.current(media,recipient).orElseThrow();
        audit.audit(actor,active?"MEDIA_REUSE_GRANTED":"MEDIA_REUSE_REVOKED","MEDIA_RESOURCE",media,
            Map.of("userId",recipient,"active",wasActive),Map.of("userId",recipient,"active",active),now);
        return new Grant(value.userId(),value.active(),value.grantedAt(),value.revokedAt(),true);
    }
}
