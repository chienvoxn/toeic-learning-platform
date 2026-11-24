package com.toeic.backend.system.application;

import com.toeic.backend.identity.application.RbacMutationGuard;
import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.identity.infrastructure.RbacRepository;
import com.toeic.backend.system.infrastructure.ModerationRepository;
import com.toeic.backend.system.infrastructure.ModerationRepository.*;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** G1: khóa nhóm rồi toàn bộ thành viên, kiểm batch đã duyệt, đổi pointer và audit cùng transaction. */
@Service
@PreAuthorize("isAuthenticated()")
public class GroupPublicationService {
    public record Member(UUID questionId,UUID questionVersionId,long expectedRevision,UUID expectedPublishedVersionId,int order) { }
    public record Command(long expectedRevision,UUID expectedPublishedVersionId,List<Member> members) {
        public Command { members=List.copyOf(members); }
    }
    private record Selected(Member command,Parent parent,Version version,Evidence evidence,int part) { }
    private final JdbcTemplate jdbc;
    private final ModerationRepository data;
    private final RbacMutationGuard rights;
    private final MediaPublicationPolicy media;
    private final RbacRepository audit;
    private final ModerationService moderation;
    private final Clock clock;
    public GroupPublicationService(JdbcTemplate jdbc,ModerationRepository data,RbacMutationGuard rights,
            MediaPublicationPolicy media,RbacRepository audit,ModerationService moderation,Clock clock) {
        this.jdbc=jdbc;this.data=data;this.rights=rights;this.media=media;this.audit=audit;this.moderation=moderation;this.clock=clock;
    }
    @Transactional
    public Map<String,Object> publish(UUID group,UUID version,Command command) {
        validateCommand(command);
        jdbc.query("SELECT pg_advisory_xact_lock_shared(73532002)",r->{});
        var principal=SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(principal instanceof SessionPrincipal p))throw new AccessDeniedException("Session required");
        var actor=rights.state(p.userId());
        if(!actor.active()||!actor.admin()||!actor.permissions().contains("question:publish"))throw new AccessDeniedException("Active admin publisher required");
        var parent=data.parent(Kind.groups,group,true);var groupVersion=data.version(Kind.groups,group,version);
        var evidence=data.evidence(Kind.groups,version);
        eligible(Kind.groups,parent,groupVersion,evidence,command.expectedRevision(),command.expectedPublishedVersionId(),actor.userId());
        // Group lock prevents authoring from attaching, moving or archiving members during this batch.
        var selected=new ArrayList<Selected>();
        for(var member:command.members().stream().sorted(Comparator.comparing(m->m.questionId().toString())).toList()) {
            var q=data.parent(Kind.questions,member.questionId(),true);
            if(!group.equals(q.groupId()))throw ModerationException.conflict("GROUP_MEMBER_MISMATCH");
            var v=data.version(Kind.questions,q.id(),member.questionVersionId());var e=data.evidence(Kind.questions,v.id());
            eligible(Kind.questions,q,v,e,member.expectedRevision(),member.expectedPublishedVersionId(),actor.userId());
            var row=jdbc.queryForMap("""
                    SELECT v.group_version_id,v.group_question_order,v.question_text,p.part_number
                    FROM question_versions v JOIN questions q USING(question_id)
                    JOIN toeic_parts p USING(part_id) WHERE v.question_version_id=?
                    """,v.id());
            if(!version.equals(row.get("group_version_id"))||!Objects.equals(member.order(),row.get("group_question_order")))
                throw ModerationException.conflict("GROUP_MEMBER_SNAPSHOT_MISMATCH");
            int part=((Number)row.get("part_number")).intValue();
            if(!Set.of(3,4,6,7).contains(part)||row.get("question_text")==null||row.get("question_text").toString().isBlank()
                    ||jdbc.queryForObject("SELECT count(*) FROM question_choices WHERE question_version_id=? AND is_correct",Integer.class,v.id())!=1)
                throw ModerationException.conflict("QUESTION_INCOMPLETE");
            selected.add(new Selected(member,q,v,e,part));
        }
        if(selected.stream().map(Selected::part).distinct().count()!=1)throw ModerationException.conflict("GROUP_PART_MISMATCH");
        var required=jdbc.query("SELECT question_id FROM questions WHERE group_id=? AND status<>'ARCHIVED' AND current_published_version_id IS NOT NULL",
                (r,n)->r.getObject(1,UUID.class),group);
        var supplied=new HashSet<>(command.members().stream().map(Member::questionId).toList());
        if(!supplied.containsAll(required))throw ModerationException.conflict("GROUP_MEMBERS_REQUIRED");
        var targets=new ArrayList<MediaPublicationPolicy.Target>();targets.add(new MediaPublicationPolicy.Target(Kind.groups,version));
        for(var q:selected)targets.add(new MediaPublicationPolicy.Target(Kind.questions,q.version().id()));
        media.validateBatch(targets,actor);
        publishOne(Kind.groups,parent,groupVersion,evidence,actor.userId());
        for(var q:selected)publishOne(Kind.questions,q.parent(),q.version(),q.evidence(),actor.userId());
        return Map.of("group",moderation.detail(Kind.groups,group,version),"members",command.members());
    }
    private void eligible(Kind kind,Parent parent,Version version,Evidence evidence,long expected,UUID pointer,UUID actor) {
        if("ARCHIVED".equals(parent.status()))throw ModerationException.conflict("PARENT_ARCHIVED");
        if(!data.complete(kind,parent.id(),evidence))throw ModerationException.conflict("CONTRIBUTOR_EVIDENCE_REQUIRED");
        if(expected<0||expected==Long.MAX_VALUE||evidence.revision()!=expected||!Objects.equals(parent.pointer(),pointer))
            throw ModerationException.conflict("STALE_REVISION");
        if(!Objects.equals(evidence.source(),parent.pointer()))throw ModerationException.conflict("SOURCE_VERSION_CHANGED");
        if(!"APPROVED".equals(version.status()))throw ModerationException.conflict("MODERATION_STATE_CONFLICT");
        if(!Objects.equals(evidence.approved(),evidence.revision()))throw ModerationException.conflict("APPROVAL_REVISION_MISMATCH");
        if(data.contributor(kind,parent.id(),evidence,actor))throw ModerationException.conflict("SELF_REVIEW_FORBIDDEN");
    }
    private void publishOne(Kind kind,Parent parent,Version version,Evidence evidence,UUID actor) {
        data.publish(kind,parent.id(),version.id(),null);data.publishedRevision(kind,version.id());
        data.decision(kind,version.id(),evidence.revision(),actor,"publish",null);
        String resource=kind==Kind.groups?"QUESTION_GROUP":"QUESTION";
        audit.audit(actor,resource+"_PUBLISHED",resource,parent.id(),Map.of("status","APPROVED","versionId",version.id(),"revision",evidence.revision()),
                Map.of("status","PUBLISHED","versionId",version.id(),"revision",evidence.revision()+1),OffsetDateTime.now(clock));
    }
    private void validateCommand(Command command) {
        if(command==null||command.expectedRevision()<0||command.members().isEmpty()||command.members().size()>200)throw ModerationException.invalid();
        var questions=new HashSet<UUID>();var versions=new HashSet<UUID>();var orders=new HashSet<Integer>();
        for(var member:command.members())if(member==null||member.questionId()==null||member.questionVersionId()==null||member.expectedRevision()<0
                ||member.order()<1||member.order()>command.members().size()||!questions.add(member.questionId())||!versions.add(member.questionVersionId())||!orders.add(member.order()))
            throw ModerationException.invalid();
    }
}
