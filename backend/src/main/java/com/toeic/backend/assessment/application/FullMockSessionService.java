package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.*;
import com.toeic.backend.assessment.infrastructure.*;
import com.toeic.backend.system.infrastructure.ModerationRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Khóa đề trước version như retire/publish; chỉ pin mapping và blueprint đã kiểm, không thay câu từ bank. */
@Service
@PreAuthorize("hasAuthority('exam:manage:self') and @practiceAccess.isActor(#actorId)")
public class FullMockSessionService {
    private final JdbcTemplate jdbc;private final ModerationRepository moderation;private final ExamDraftRepository drafts;
    private final ExamPublicationPolicy publication;private final PracticeAttemptRepository attempts;private final PracticeSessionService sessions;private final Clock clock;
    private final com.toeic.backend.identity.application.RbacMutationGuard rights;
    private final com.toeic.backend.system.application.MediaPublicationPolicy media;
    public FullMockSessionService(JdbcTemplate jdbc,ModerationRepository moderation,ExamDraftRepository drafts,ExamPublicationPolicy publication,PracticeAttemptRepository attempts,PracticeSessionService sessions,Clock clock,com.toeic.backend.identity.application.RbacMutationGuard rights,com.toeic.backend.system.application.MediaPublicationPolicy media){this.jdbc=jdbc;this.moderation=moderation;this.drafts=drafts;this.publication=publication;this.attempts=attempts;this.sessions=sessions;this.clock=clock;this.rights=rights;this.media=media;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PracticeSessionDtos.Session create(UUID actorId,UUID version){
        if(version==null)throw PracticeException.invalid();
        jdbc.query("SELECT pg_advisory_xact_lock_shared(73532002)",r->{});var actor=rights.state(actorId);
        if(!actor.active()||!actor.permissions().contains("exam:manage:self"))throw new org.springframework.security.access.AccessDeniedException("Active exam actor required");
        var ids=jdbc.query("SELECT test_id FROM test_versions WHERE test_version_id=?",(r,n)->r.getObject(1,UUID.class),version);
        if(ids.isEmpty())throw ExamDraftException.missing();UUID test=ids.getFirst();
        var parent=moderation.parent(ModerationRepository.Kind.tests,test,true);var draft=drafts.find(test,version,true).orElseThrow(ExamDraftException::missing);
        if(!parent.status().equals("PUBLISHED")||!version.equals(parent.pointer())||!draft.status().equals("PUBLISHED"))throw ExamDraftException.conflict("TEST_NOT_OFFERED");
        var blueprint=publication.publishedBlueprint(version);publication.validateWith(version,blueprint);
        var publishers=jdbc.query("SELECT actor_id FROM moderation_decisions WHERE kind='tests' AND version_id=? AND action='publish' ORDER BY created_at DESC,revision DESC LIMIT 1",(r,n)->r.getObject(1,UUID.class),version);
        if(publishers.isEmpty())throw ExamDraftException.conflict("PUBLICATION_EVIDENCE_REQUIRED");
        media.validate(ModerationRepository.Kind.tests,version,rights.state(publishers.getFirst()),null);
        var doc=draft.document();var snapshot=new TestAttemptSnapshot(test,version,draft.version(),doc.title(),blueprint);
        var pins=doc.sections().stream().flatMap(s->s.parts().stream()).flatMap(p->p.questions().stream()).map(q->new PracticeAttempt.Pin(q.questionVersionId(),q.groupVersionId())).toList();
        var attempt=PracticeAttempt.fullMock(actorId,OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS),snapshot,pins);
        attempts.persist(attempt);return sessions.view(actorId,attempt);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> catalog(UUID actorId){
        // Metadata only. Start rechecks eligibility under the aggregate locks; no question or answer data here.
        return jdbc.queryForList("""
            SELECT t.test_id AS "testId",v.test_version_id AS "versionId",v.version_number AS version,
              d.document->>'title' AS title,d.document->>'description' AS description,v.total_questions AS "totalQuestions",
              v.duration_seconds AS "durationSeconds",v.blueprint_snapshot->>'code' AS "blueprintCode",
              (v.blueprint_snapshot->>'version')::int AS "blueprintVersion"
            FROM tests t JOIN test_versions v ON v.test_version_id=t.current_published_version_id AND v.test_id=t.test_id
            JOIN test_version_drafts d ON d.test_version_id=v.test_version_id
            WHERE t.status='PUBLISHED' AND v.status='PUBLISHED' AND v.blueprint_snapshot IS NOT NULL
            ORDER BY v.published_at DESC,v.test_version_id
            LIMIT 100
            """);
    }
}
