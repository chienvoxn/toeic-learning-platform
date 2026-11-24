package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.infrastructure.*;
import com.toeic.backend.identity.application.RbacMutationGuard;
import com.toeic.backend.identity.infrastructure.RbacRepository;
import com.toeic.backend.system.application.*;
import com.toeic.backend.system.infrastructure.ModerationRepository;
import com.toeic.backend.system.infrastructure.ModerationRepository.Kind;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Dùng moderation chung cho TestVersion; fork/retire giữ parent lock và provenance thật. */
@Service
@PreAuthorize("isAuthenticated() and @examAccess.actor(#actorId)")
@Transactional
public class ExamWorkflowService {
    private final ExamDraftRepository drafts;private final ModerationRepository moderation;private final ModerationService workflow;
    private final RbacMutationGuard rights;private final ExamMutationAccess access;private final TestBlueprintRepository blueprints;
    private final ExamPublicationPolicy publication;private final RbacRepository audit;private final JdbcTemplate jdbc;private final Clock clock;
    public ExamWorkflowService(ExamDraftRepository drafts,ModerationRepository moderation,ModerationService workflow,RbacMutationGuard rights,ExamMutationAccess access,TestBlueprintRepository blueprints,ExamPublicationPolicy publication,RbacRepository audit,JdbcTemplate jdbc,Clock clock){
        this.drafts=drafts;this.moderation=moderation;this.workflow=workflow;this.rights=rights;this.access=access;this.blueprints=blueprints;this.publication=publication;this.audit=audit;this.jdbc=jdbc;this.clock=clock;
    }
    private RbacMutationGuard.State state(UUID actor){var a=rights.state(actor);if(!a.active())throw new AccessDeniedException("Active actor required");return a;}
    private boolean manager(RbacMutationGuard.State a){return a.permissions().contains("test:manage:self")||access.any(a);}
    private void readable(RbacMutationGuard.State a,UUID test){
        if(access.reviewer(a))return;
        access.manage(a);if(!drafts.owns(test,a.userId(),access.any(a),false))throw ExamDraftException.missing();
    }
    @Transactional(readOnly=true)
    public Map<String,Boolean> capabilities(UUID actorId){var a=state(actorId);return Map.of("manage",manager(a),"readQuestions",a.permissions().contains("question:read"),"review",a.admin()&&a.permissions().contains("test:review"),"publish",a.admin()&&a.permissions().contains("test:publish"),"blueprints",a.admin()&&access.any(a));}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(UUID actorId,int page){
        var a=state(actorId);if(!manager(a)&&!access.reviewer(a))throw new AccessDeniedException("Test permission required");if(page<0||page>100000)throw ExamDraftException.invalid();
        return drafts.list(actorId,access.any(a)||access.reviewer(a),page);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> versions(UUID actorId,UUID test){var a=state(actorId);readable(a,test);return drafts.versions(test);}
    @Transactional(readOnly=true)
    public Map<String,Object> read(UUID actorId,UUID test,UUID version){
        var a=state(actorId);readable(a,test);var d=drafts.find(test,version,false).orElseThrow(ExamDraftException::missing);
        var e=moderation.evidence(Kind.tests,d.versionId());boolean complete=moderation.complete(Kind.tests,test,e);
        boolean banned=moderation.contributor(Kind.tests,test,e,actorId);var parent=moderation.parent(Kind.tests,test,false);
        var result=new LinkedHashMap<String,Object>();result.put("testId",test);result.put("versionId",d.versionId());result.put("ownerId",d.ownerId());result.put("version",d.version());result.put("revision",d.revision());result.put("status",d.status());result.put("document",d.document());
        result.put("blueprintId",drafts.blueprint(d.versionId()));result.put("currentPublishedVersionId",parent.pointer());result.put("sourceVersionId",e==null?null:e.source());result.put("evidenceComplete",complete);result.put("approvedRevision",e==null?null:e.approved());
        boolean author=access.any(a)||actorId.equals(d.ownerId())&&a.permissions().contains("test:manage:self");
        boolean pub=a.admin()&&a.permissions().contains("test:publish"),review=a.admin()&&a.permissions().contains("test:review");
        result.put("capabilities",Map.of("edit",author&&d.status().equals("DRAFT")&&d.document()!=null&&!drafts.pinned(d.versionId()),
                "submit",complete&&author&&d.status().equals("DRAFT"),"approve",complete&&review&&!banned&&d.status().equals("IN_REVIEW"),
                "return",complete&&review&&d.status().equals("IN_REVIEW"),"revoke",complete&&review&&d.status().equals("APPROVED"),
                "publish",complete&&pub&&!banned&&d.status().equals("APPROVED"),"retire",pub&&d.status().equals("PUBLISHED")&&d.versionId().equals(parent.pointer()),
                "fork",author&&d.status().equals("PUBLISHED")&&d.versionId().equals(parent.pointer())&&!drafts.working(test)));
        return result;
    }
    public Map<String,Object> transition(UUID actorId,UUID test,UUID version,String command,long expected,String reason){
        workflow.transition(Kind.tests,test,version,command,expected,reason,null);return read(actorId,test,version);
    }
    private ExamDraftRepository.Draft authorLock(UUID actorId,UUID test,UUID version,long expected){
        var a=access.lock(actorId);access.manage(a);if(!drafts.owns(test,actorId,access.any(a),true))throw ExamDraftException.missing();
        var d=drafts.find(test,version,true).orElseThrow(ExamDraftException::missing);
        if(expected<0||expected>=Integer.MAX_VALUE||expected!=d.revision())throw ExamDraftException.stale();return d;
    }
    public Map<String,Object> blueprint(UUID actorId,UUID test,UUID version,long expected,UUID blueprint){
        var d=authorLock(actorId,test,version,expected);if(!d.status().equals("DRAFT")||d.document()==null||drafts.pinned(version))throw ExamDraftException.immutable();
        var b=blueprints.active(blueprint);TestBlueprintService.validate(b);blueprints.verifyItems(b);drafts.selectBlueprint(version,blueprint);moderation.record(Kind.tests,test,version,null,actorId,false);
        audit.audit(actorId,"TEST_UPDATED","TEST",test,Map.of("versionId",version,"revision",expected),Map.of("versionId",version,"revision",expected+1,"blueprintId",blueprint),OffsetDateTime.now(clock));return read(actorId,test,version);
    }
    public Map<String,Object> fork(UUID actorId,UUID test,UUID source,long expected){
        var d=authorLock(actorId,test,source,expected);var p=moderation.parent(Kind.tests,test,false);
        if(!d.status().equals("PUBLISHED")||!source.equals(p.pointer())||!p.status().equals("PUBLISHED"))throw ExamDraftException.conflict("CURRENT_PUBLISHED_REQUIRED");
        if(drafts.working(test))throw ExamDraftException.conflict("WORKING_DRAFT_EXISTS");
        publication.publishedBlueprint(source); // Exact pins copied; obsolete bank references require an explicit DRAFT edit.
        var copy=drafts.fork(d,actorId);moderation.record(Kind.tests,test,copy.versionId(),source,actorId,true);
        audit.audit(actorId,"TEST_VERSION_FORKED","TEST",test,Map.of("sourceVersionId",source),Map.of("versionId",copy.versionId(),"revision",0),OffsetDateTime.now(clock));return read(actorId,test,copy.versionId());
    }
    public Map<String,Object> retire(UUID actorId,UUID test,UUID version,long expected){
        var a=access.lock(actorId);if(!a.admin()||!a.permissions().contains("test:publish"))throw new AccessDeniedException("Active Admin publisher required");
        var p=moderation.parent(Kind.tests,test,true);var d=drafts.find(test,version,true).orElseThrow(ExamDraftException::missing);
        if(expected<0||expected>=Integer.MAX_VALUE||d.revision()!=expected)throw ExamDraftException.stale();
        if(!d.status().equals("PUBLISHED")||!version.equals(p.pointer()))throw ExamDraftException.conflict("CURRENT_PUBLISHED_REQUIRED");
        jdbc.update("UPDATE test_versions SET status='RETIRED',retired_at=CURRENT_TIMESTAMP WHERE test_version_id=?",version);
        jdbc.update("UPDATE tests SET status='RETIRED',updated_at=CURRENT_TIMESTAMP WHERE test_id=?",test);
        moderation.publishedRevision(Kind.tests,version);moderation.decision(Kind.tests,version,expected,actorId,"retire",null);
        audit.audit(actorId,"TEST_RETIRED","TEST",test,Map.of("versionId",version,"revision",expected,"status","PUBLISHED"),Map.of("versionId",version,"revision",expected+1,"status","RETIRED"),OffsetDateTime.now(clock));return read(actorId,test,version);
    }
}
