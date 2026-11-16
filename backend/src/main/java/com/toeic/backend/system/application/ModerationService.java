package com.toeic.backend.system.application;

import com.toeic.backend.identity.application.*;
import com.toeic.backend.identity.infrastructure.RbacRepository;
import com.toeic.backend.system.infrastructure.ModerationRepository;
import com.toeic.backend.system.infrastructure.ModerationRepository.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Dieu phoi review/publication: RBAC -> parent -> version -> media/grant; mutation va audit nguyen tu. */
@Service
@PreAuthorize("isAuthenticated()")
public class ModerationService {
    private final ModerationRepository data;private final RbacMutationGuard rights;private final JdbcTemplate jdbc;
    private final RbacRepository audit;private final MediaPublicationPolicy media;private final Clock clock;
    public ModerationService(ModerationRepository data,RbacMutationGuard rights,JdbcTemplate jdbc,RbacRepository audit,MediaPublicationPolicy media,Clock clock){this.data=data;this.rights=rights;this.jdbc=jdbc;this.audit=audit;this.media=media;this.clock=clock;}
    private RbacMutationGuard.State actor(){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !(auth.getPrincipal() instanceof SessionPrincipal p))throw new AccessDeniedException("Session required");
        var state=rights.state(p.userId());if(!state.active())throw new AccessDeniedException("Active actor required");return state;
    }
    private boolean author(RbacMutationGuard.State a,Kind k,UUID owner){return a.permissions().contains(k.permission+":manage:any") || a.userId().equals(owner)&&a.permissions().contains(k.permission+":manage:self");}
    private boolean admin(RbacMutationGuard.State a,Kind k,String permission){return a.admin()&&a.permissions().contains(k.permission+":"+permission);}
    private void readable(RbacMutationGuard.State a,Kind k,Parent parent){
        if(author(a,k,parent.owner())||admin(a,k,"review")||admin(a,k,"publish"))return;
        if(a.permissions().contains(k.permission+":manage:self"))throw ModerationException.missing();
        throw new AccessDeniedException("Moderation permission required");
    }
    @Transactional(readOnly=true)
    public Map<String,Object> capabilities(){
        var a=actor();var result=new LinkedHashMap<String,Object>();
        for(var k:Kind.values())result.put(k.name(),Map.of("author",a.permissions().contains(k.permission+":manage:self")||a.permissions().contains(k.permission+":manage:any"),"review",admin(a,k,"review"),"publish",admin(a,k,"publish")));
        return result;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> list(Kind kind,int page){
        if(page<0||page>10000)throw ModerationException.invalid();var a=actor();
        boolean any=a.permissions().contains(kind.permission+":manage:any")||admin(a,kind,"review")||admin(a,kind,"publish");
        if(!any&&!a.permissions().contains(kind.permission+":manage:self"))throw new AccessDeniedException("Moderation permission required");
        return data.list(kind,a.userId(),any,page);
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Kind kind,UUID parent,UUID version){var p=data.parent(kind,parent,false);var a=actor();readable(a,kind,p);var v=data.version(kind,parent,version);return view(a,kind,p,v);}
    private Map<String,Object> view(RbacMutationGuard.State a,Kind k,Parent p,Version v){
        var e=data.evidence(k,v.id());boolean complete=data.complete(k,p.id(),e);boolean banned=data.contributor(k,p.id(),e,a.userId());
        var result=new LinkedHashMap<String,Object>();result.put("parentId",p.id());result.put("versionId",v.id());result.put("status",v.status());result.put("parentStatus",p.status());
        result.put("revision",e==null?null:e.revision());result.put("approvedRevision",e==null?null:e.approved());result.put("sourceVersionId",e==null?null:e.source());result.put("evidenceComplete",complete);
        result.put("payload",data.payload(k,v.id()));result.put("decisions",data.decisions(k,v.id()));
        boolean active=!p.status().equals("ARCHIVED")&&complete;
        result.put("capabilities",Map.of("submit",active&&author(a,k,p.owner())&&v.status().equals("DRAFT"),
            "approve",active&&!banned&&admin(a,k,"review")&&v.status().equals("IN_REVIEW"),
            "return",active&&admin(a,k,"review")&&v.status().equals("IN_REVIEW"),
            "revoke",active&&admin(a,k,"review")&&v.status().equals("APPROVED"),
            "publish",active&&!banned&&admin(a,k,"publish")&&v.status().equals("APPROVED")&&k!=Kind.groups&&p.groupId()==null));return result;
    }
    @Transactional
    public Map<String,Object> transition(Kind kind,UUID parent,UUID version,String command,long expected,String reason,String visibility){
        if(expected<0||!Set.of("submit","approve","return","revoke","publish").contains(command))throw ModerationException.invalid();
        boolean needsReason=Set.of("return","revoke").contains(command);
        if(needsReason&&(reason==null||reason.isBlank()||reason.length()>2000)||!needsReason&&reason!=null)throw ModerationException.invalid();
        if(command.equals("publish")&&kind==Kind.contents){if(visibility==null||!Set.of("PUBLIC","AUTHENTICATED").contains(visibility))throw ModerationException.invalid();}
        else if(visibility!=null)throw ModerationException.invalid();
        // Shared with RBAC writers: authority cannot change while this command waits on an aggregate.
        jdbc.query("SELECT pg_advisory_xact_lock_shared(73532002)",r->{});
        var a=actor();var p=data.parent(kind,parent,true);readable(a,kind,p);var v=data.version(kind,parent,version);var e=data.evidence(kind,version);
        if(command.equals("submit")){if(!author(a,kind,p.owner()))throw new AccessDeniedException("Authoring permission required");}
        else if(!admin(a,kind,command.equals("publish")?"publish":"review"))throw new AccessDeniedException("Admin permission required");
        if(p.status().equals("ARCHIVED"))throw ModerationException.conflict("PARENT_ARCHIVED");
        if(!data.complete(kind,parent,e))throw ModerationException.conflict("CONTRIBUTOR_EVIDENCE_REQUIRED");
        if(e.revision()!=expected || expected==Long.MAX_VALUE)throw ModerationException.conflict("STALE_REVISION");
        String required=switch(command){case "submit"->"DRAFT";case "approve","return"->"IN_REVIEW";case "revoke","publish"->"APPROVED";default->throw ModerationException.invalid();};
        if(!required.equals(v.status()))throw ModerationException.conflict("MODERATION_STATE_CONFLICT");
        if(Set.of("approve","publish").contains(command)&&data.contributor(kind,parent,e,a.userId()))throw ModerationException.conflict("SELF_REVIEW_FORBIDDEN");
        if(command.equals("publish")&&(kind==Kind.groups||p.groupId()!=null))throw ModerationException.conflict("GROUP_PUBLICATION_NOT_AVAILABLE");
        if(command.equals("publish")&&!Objects.equals(e.approved(),e.revision()))throw ModerationException.conflict("APPROVAL_REVISION_MISMATCH");
        if(command.equals("submit"))validate(kind,version);
        String next=switch(command){case "submit"->"IN_REVIEW";case "approve"->"APPROVED";case "return","revoke"->"DRAFT";default->"PUBLISHED";};
        if(command.equals("publish")){
            validate(kind,version);media.validate(kind,version,a,visibility);data.publish(kind,parent,version,visibility);
            jdbc.update("UPDATE moderation_versions SET revision=revision+1 WHERE kind=? AND version_id=?",kind.name(),version);
        }else data.state(kind,version,next,command.equals("approve")?e.revision()+1:null);
        if(command.equals("submit"))data.contributor(kind,version,a.userId(),"SUBMITTED");
        data.decision(kind,version,expected,a.userId(),command,reason);
        String resource=kind==Kind.contents?"CONTENT":kind==Kind.questions?"QUESTION":"QUESTION_GROUP";
        String action=switch(command){case "submit"->"SUBMITTED";case "approve"->"APPROVED";case "return"->"RETURNED";case "revoke"->"APPROVAL_REVOKED";default->"PUBLISHED";};
        audit.audit(a.userId(),resource+"_"+action,resource,parent,Map.of("status",v.status(),"versionId",version,"revision",expected),Map.of("status",next,"versionId",version,"revision",expected+1),OffsetDateTime.now(clock));
        return view(a,kind,data.parent(kind,parent,false),new Version(version,next));
    }
    private void validate(Kind k,UUID version){
        var payload=data.payload(k,version);
        if(k==Kind.contents){if(payload.get("title")==null||payload.get("title").toString().isBlank()||payload.get("body")==null||payload.get("body").toString().isBlank())throw ModerationException.conflict("CONTENT_INCOMPLETE");}
        if(k==Kind.questions){
            int count=jdbc.queryForObject("SELECT count(*) FROM question_choices WHERE question_version_id=? AND is_correct",Integer.class,version);
            if(count!=1||payload.get("question_text")==null||payload.get("question_text").toString().isBlank())throw ModerationException.conflict("QUESTION_INCOMPLETE");
        }
    }
}
