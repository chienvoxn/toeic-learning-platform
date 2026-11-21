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
    public ExamDraftService(ExamDraftRepository drafts, PracticePublicationRepository bank, ExamAccess access, RbacRepository audit, Clock clock, QuestionVersionRepository versions) {
        this.drafts=drafts; this.bank=bank; this.access=access; this.audit=audit; this.clock=clock; this.versions=versions;
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
        validate(document); var now=OffsetDateTime.now(clock); var test=UUID.randomUUID(); var version=UUID.randomUUID();
        drafts.create(actorId,test,version,document,now);
        audit.audit(actorId,"TEST_CREATED","TEST",test,null,Map.of("versionId",version,"revision",0,"status","DRAFT"),now);
        return drafts.find(test,version,false).orElseThrow(ExamDraftException::missing);
    }
    public ExamDraftRepository.Draft update(UUID actorId, UUID test, UUID version, int revision, ExamDraftDocument document) {
        owned(actorId,test,true); var draft=drafts.find(test,version,true).orElseThrow(ExamDraftException::missing);
        if(!"DRAFT".equals(draft.status()) || draft.document()==null || drafts.pinned(version))throw ExamDraftException.immutable();
        if(revision!=draft.revision())throw ExamDraftException.stale();
        validate(document); var now=OffsetDateTime.now(clock); drafts.update(draft,document,now);
        audit.audit(actorId,"TEST_UPDATED","TEST",test,Map.of("versionId",version,"revision",revision),Map.of("versionId",version,"revision",revision+1),now);
        return drafts.find(test,version,false).orElseThrow(ExamDraftException::missing);
    }
    private void owned(UUID actor, UUID test, boolean lock) {
        if(!drafts.owns(test,actor,access.has("test:manage:any"),lock))throw ExamDraftException.missing();
    }
    private void validate(ExamDraftDocument doc) {
        if(doc==null || doc.title()==null || doc.title().isBlank() || doc.title().length()>255
                || doc.description()!=null && doc.description().length()>10000
                || !Set.of("PRACTICE","LISTENING","READING","FULL").contains(doc.testType()==null?"":doc.testType())
                || doc.durationSeconds()!=null && doc.durationSeconds()<1 || doc.sections().size()>2)throw ExamDraftException.invalid();
        var refs=new ArrayList<ExamDraftDocument.Question>(); var parts=new HashSet<Integer>(); int lastSection=0;
        var partByQuestion=new HashMap<UUID,Integer>();
        for(var section:doc.sections()) {
            int position="LISTENING".equals(section.type())?1:"READING".equals(section.type())?2:0;
            if(position<=lastSection || doc.testType().equals("LISTENING")&&position!=1 || doc.testType().equals("READING")&&position!=2)throw ExamDraftException.invalid();
            lastSection=position; int lastPart=0;
            for(var part:section.parts()) {
                int number=part.partNumber();
                if(number<1 || number>7 || number<=lastPart || !parts.add(number) || (number<=4)!=(position==1))throw ExamDraftException.invalid();
                lastPart=number;
                for(var q:part.questions()) {
                    if(q.questionVersionId()==null || partByQuestion.put(q.questionVersionId(),number)!=null)throw ExamDraftException.invalid();
                    refs.add(q);
                }
            }
        }
        // DRAFT có thể thiếu Section/câu/thời lượng; chưa áp blueprint TOEIC chính thức.
        if(refs.isEmpty())return;
        if(!access.has("question:read"))throw new AccessDeniedException("Question read permission required");
        var now=OffsetDateTime.now(clock); var pool=bank.practicePool(parts,null,null,now);
        var selected=pool.stream().filter(m->partByQuestion.containsKey(m.candidate().questionVersionId())).toList();
        if(selected.size()!=refs.size())throw ExamDraftException.reference();
        bank.lockSelected(selected);
        pool=bank.practicePool(parts,null,null,now);
        var byId=pool.stream().collect(Collectors.toMap(m->m.candidate().questionVersionId(),m->m));
        for(var ref:refs) {
            var m=byId.get(ref.questionVersionId());
            if(m==null || !m.eligible() || m.candidate().partNumber()!=partByQuestion.get(ref.questionVersionId())
                    || !Objects.equals(ref.groupVersionId(),m.candidate().groupVersionId()))throw ExamDraftException.reference();
        }
        var seenGroups=new HashSet<UUID>();
        for(int i=0;i<refs.size();) {
            var ref=refs.get(i); var member=byId.get(ref.questionVersionId());
            if(member==null || !member.eligible() || member.candidate().partNumber()!=partByQuestion.get(ref.questionVersionId())
                    || !Objects.equals(ref.groupVersionId(),member.candidate().groupVersionId()))throw ExamDraftException.reference();
            if(ref.groupVersionId()==null) { i++; continue; }
            if(!seenGroups.add(ref.groupVersionId()))throw ExamDraftException.reference();
            var group=pool.stream().filter(m->ref.groupVersionId().equals(m.candidate().groupVersionId())).toList();
            if(group.stream().anyMatch(m->!m.eligible() || m.groupOrder()==null || m.groupOrder()<1
                    || m.candidate().partNumber()!=member.candidate().partNumber())
                    || group.stream().map(PracticePublicationRepository.PoolMember::groupOrder).distinct().count()!=group.size())throw ExamDraftException.reference();
            var ordered=group.stream().sorted(Comparator.comparing(PracticePublicationRepository.PoolMember::groupOrder)).toList();
            for(var m:ordered) {
                if(i>=refs.size() || !refs.get(i).questionVersionId().equals(m.candidate().questionVersionId())
                        || !Objects.equals(refs.get(i).groupVersionId(),ref.groupVersionId()))throw ExamDraftException.reference();
                i++;
            }
        }
    }
}
