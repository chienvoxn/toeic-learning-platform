package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.ExamDraftDocument;
import com.toeic.backend.assessment.infrastructure.ExamDraftRepository;
import com.toeic.backend.questionbank.infrastructure.PracticePublicationRepository;
import com.toeic.backend.questionbank.infrastructure.QuestionVersionRepository;
import com.toeic.backend.identity.infrastructure.RbacRepository;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Biên soạn DRAFT có ownership/revision/audit; không tự publish hoặc tạo ngân hàng câu hỏi. */
@Service
@PreAuthorize("hasAnyAuthority('test:manage:self','test:manage:any') and @examAccess.actor(#actorId)")
@Transactional
public class ExamDraftService {
    private final ExamDraftRepository drafts;
    private final PracticePublicationRepository bank;
    private final ExamAccess access;
    private final RbacRepository audit;
    private final Clock clock;
    private final QuestionVersionRepository versions;
    private final ExamReferencePolicy references;
    private final ExamMutationAccess mutations;
    private final com.toeic.backend.system.application.ModerationEvidence evidence;
    public ExamDraftService(ExamDraftRepository drafts, PracticePublicationRepository bank, ExamAccess access, RbacRepository audit, Clock clock, QuestionVersionRepository versions,ExamReferencePolicy references,ExamMutationAccess mutations,com.toeic.backend.system.application.ModerationEvidence evidence) {
        this.drafts=drafts; this.bank=bank; this.access=access; this.audit=audit; this.clock=clock; this.versions=versions;this.references=references;this.mutations=mutations;this.evidence=evidence;
    }
    public record UnitQuestion(UUID questionVersionId, UUID groupVersionId, String questionText) { }
    public record QuestionUnit(int partNumber, List<UnitQuestion> questions) { }
    @Transactional(readOnly=true)
    public Map<String,Boolean> capabilities(UUID actorId) { return Map.of("manage",true,"readQuestions",access.has("question:read")); }
    /** Danh sách chọn nguyên nhóm từ cùng nguồn publication; không trả đáp án hay tài nguyên media. */
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<QuestionUnit> units(UUID actorId, int part, int page) {
        if(part<1 || part>7 || page<0 || page>100000)throw ExamDraftException.invalid();
        if(!access.has("question:read"))throw new AccessDeniedException("Question read permission required");
        var grouped=new LinkedHashMap<String,List<PracticePublicationRepository.PoolMember>>();
        for(var m:bank.practicePool(Set.of(part),null,null,OffsetDateTime.now(clock))) {
            var c=m.candidate();var key=c.groupVersionId()==null?"q:"+c.questionVersionId():"g:"+c.groupVersionId();
            grouped.computeIfAbsent(key,k->new ArrayList<>()).add(m);
        }
        var available=new ArrayList<List<PracticePublicationRepository.PoolMember>>();
        for(var members:grouped.values()) {
            if(members.stream().anyMatch(m->!m.eligible() || m.candidate().partNumber()!=part))continue;
            if(members.getFirst().candidate().groupVersionId()!=null) {
                if(members.stream().anyMatch(m->m.groupOrder()==null || m.groupOrder()<1)
                        || members.stream().map(PracticePublicationRepository.PoolMember::groupOrder).distinct().count()!=members.size())continue;
                members.sort(Comparator.comparing(PracticePublicationRepository.PoolMember::groupOrder));
            }
            available.add(members);
        }
        return available.stream().skip((long)page*50).limit(50).map(members->new QuestionUnit(part,members.stream()
                .map(m->new UnitQuestion(m.candidate().questionVersionId(),m.candidate().groupVersionId(),
                    versions.findById(m.candidate().questionVersionId()).orElseThrow(ExamDraftException::reference).getQuestionText())).toList())).toList();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(UUID actorId, int page) {
        if(page<0 || page>100000)throw ExamDraftException.invalid();
        return drafts.list(actorId,access.has("test:manage:any"),page);
    }
    @Transactional(readOnly=true)
    public ExamDraftRepository.Draft read(UUID actorId, UUID test, UUID version) {
        owned(actorId,test,false); return drafts.find(test,version,false).orElseThrow(ExamDraftException::missing);
    }
    public ExamDraftRepository.Draft create(UUID actorId, ExamDraftDocument document) {
        mutations.manage(mutations.lock(actorId));
        validate(document); var now=OffsetDateTime.now(clock); var test=UUID.randomUUID(); var version=UUID.randomUUID();
        drafts.create(actorId,test,version,document,now);
        evidence.record("tests",test,version,null,actorId,true);
        audit.audit(actorId,"TEST_CREATED","TEST",test,null,Map.of("versionId",version,"revision",0,"status","DRAFT"),now);
        return drafts.find(test,version,false).orElseThrow(ExamDraftException::missing);
    }
    public ExamDraftRepository.Draft update(UUID actorId, UUID test, UUID version, int revision, ExamDraftDocument document) {
        var a=mutations.lock(actorId);mutations.manage(a);
        if(!drafts.owns(test,actorId,mutations.any(a),true))throw ExamDraftException.missing();
        var draft=drafts.find(test,version,true).orElseThrow(ExamDraftException::missing);
        if(!"DRAFT".equals(draft.status()) || draft.document()==null || drafts.pinned(version))throw ExamDraftException.immutable();
        if(revision!=draft.revision()||revision==Integer.MAX_VALUE)throw ExamDraftException.stale();
        validate(document); var now=OffsetDateTime.now(clock); drafts.update(draft,document,now);
        evidence.record("tests",test,version,null,actorId,false);
        audit.audit(actorId,"TEST_UPDATED","TEST",test,Map.of("versionId",version,"revision",revision),Map.of("versionId",version,"revision",revision+1),now);
        return drafts.find(test,version,false).orElseThrow(ExamDraftException::missing);
    }
    private void owned(UUID actor, UUID test, boolean lock) {
        if(!drafts.owns(test,actor,access.has("test:manage:any"),lock))throw ExamDraftException.missing();
    }
    private void validate(ExamDraftDocument doc) { references.validate(doc,true); }
}
