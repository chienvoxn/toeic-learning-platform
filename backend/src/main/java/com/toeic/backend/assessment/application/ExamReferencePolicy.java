package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.ExamDraftDocument;
import com.toeic.backend.questionbank.infrastructure.PracticePublicationRepository;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Dùng chung kiểm version/nhóm và khóa publication pool; không tự thay reference. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class ExamReferencePolicy {
    private final PracticePublicationRepository bank;
    private final ExamAccess access;
    private final Clock clock;
    public ExamReferencePolicy(PracticePublicationRepository bank,ExamAccess access,Clock clock) { this.bank=bank;this.access=access;this.clock=clock; }
    public void validate(ExamDraftDocument doc,boolean requireActorRead) {
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
        if(requireActorRead && !access.has("question:read"))throw new AccessDeniedException("Question read permission required");
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
