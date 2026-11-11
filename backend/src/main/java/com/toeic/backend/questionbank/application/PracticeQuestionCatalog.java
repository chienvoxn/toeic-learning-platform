package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.infrastructure.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Public QuestionBank contract for practice; independent of Teacher question:read and authoring grants. */
@Service
@Transactional(propagation=Propagation.MANDATORY)
@PreAuthorize("hasAuthority('practice:manage:self') and @practiceAccess.isActor(#actorId)")
public class PracticeQuestionCatalog {
    public record Criteria(Set<Integer> partNumbers,Integer topicId,String difficulty) {
        public Criteria { partNumbers=Set.copyOf(partNumbers); }
    }
    public record Pin(UUID questionId,UUID questionVersionId,UUID groupId,UUID groupVersionId,int partNumber) { }
    public record Unit(int partNumber,List<Pin> questions) {
        public Unit { questions=List.copyOf(questions); }
    }
    public static class Unavailable extends RuntimeException {
        private final boolean sourceChanged;
        public Unavailable(boolean sourceChanged) { super("Practice source unavailable"); this.sourceChanged=sourceChanged; }
        public boolean sourceChanged() { return sourceChanged; }
    }
    private final PracticePublicationRepository data;
    private final QuestionVersionRepository versions;
    private final QuestionAssetRepository assets;
    private final PracticeQuestionViewMapper mapper;
    public PracticeQuestionCatalog(PracticePublicationRepository data,QuestionVersionRepository versions,
            QuestionAssetRepository assets,PracticeQuestionViewMapper mapper) {
        this.data=data;this.versions=versions;this.assets=assets;this.mapper=mapper;
    }
    private String key(Pin p) { return p.groupVersionId()==null?"q:"+p.questionVersionId():"g:"+p.groupVersionId(); }
    private List<Unit> units(List<PracticePublicationRepository.PoolMember> pool,Criteria criteria) {
        var grouped=new LinkedHashMap<String,List<PracticePublicationRepository.PoolMember>>();
        for(var m:pool) { var c=m.candidate(); var key=c.groupVersionId()==null?"q:"+c.questionVersionId():"g:"+c.groupVersionId();
            grouped.computeIfAbsent(key,k->new ArrayList<>()).add(m); }
        var result=new ArrayList<Unit>();
        for(var members:grouped.values()) {
            int part=members.getFirst().candidate().partNumber();
            if(!criteria.partNumbers().contains(part)||members.stream().anyMatch(m->!m.eligible()||m.candidate().partNumber()!=part))continue;
            if(members.getFirst().candidate().groupVersionId()!=null) {
                if(members.stream().anyMatch(m->m.groupOrder()==null||m.groupOrder()<1)
                        ||members.stream().map(PracticePublicationRepository.PoolMember::groupOrder).distinct().count()!=members.size())continue;
                members.sort(Comparator.comparing(PracticePublicationRepository.PoolMember::groupOrder));
            }
            result.add(new Unit(part,members.stream().map(m->{var c=m.candidate();return new Pin(c.questionId(),c.questionVersionId(),m.groupId(),c.groupVersionId(),part);}).toList()));
        }
        return List.copyOf(result);
    }
    public List<Unit> available(UUID actorId,Criteria criteria,OffsetDateTime now) {
        return units(data.practicePool(criteria.partNumbers(),criteria.topicId(),criteria.difficulty(),now),criteria);
    }
    public void pin(UUID actorId,Criteria criteria,List<Pin> selected,OffsetDateTime now) {
        if(selected.isEmpty())throw new Unavailable(true);
        var current=data.practicePool(criteria.partNumbers(),criteria.topicId(),criteria.difficulty(),now);
        var ids=selected.stream().map(Pin::questionVersionId).collect(java.util.stream.Collectors.toSet());
        var rows=current.stream().filter(m->ids.contains(m.candidate().questionVersionId())).toList();
        if(rows.size()!=selected.size())throw new Unavailable(true);
        data.lockSelected(rows);
        var fresh=units(data.practicePool(criteria.partNumbers(),criteria.topicId(),criteria.difficulty(),now),criteria);
        var chosen=new HashMap<String,List<Pin>>();
        selected.forEach(p->chosen.computeIfAbsent(key(p),k->new ArrayList<>()).add(p));
        for(var entry:chosen.entrySet()) {
            var unit=fresh.stream().filter(u->key(u.questions().getFirst()).equals(entry.getKey())).findFirst().orElseThrow(()->new Unavailable(true));
            if(!unit.questions().equals(entry.getValue()))throw new Unavailable(true);
        }
    }
    /** Exact pinned snapshot, even after archive/republish. Current visibility still filters each media read. */
    public PracticeQuestionViewMapper.View read(UUID actorId,UUID questionVersionId,UUID groupVersionId) {
        var version=versions.findById(questionVersionId).orElseThrow(()->new Unavailable(false));
        var group=version.getGroupVersion();
        if(!Objects.equals(groupVersionId,group==null?null:group.getGroupVersionId()))throw new Unavailable(false);
        try {
            return mapper.from(version,assets.findReadableForQuestionVersion(questionVersionId),group==null?List.of():assets.findReadableForGroupVersion(groupVersionId));
        } catch(IllegalArgumentException|IllegalStateException ex) { throw new Unavailable(false); }
    }
    /** Chỉ dùng nội bộ cho chấm bài; controller trước submit tiếp tục dùng safe read(). */
    public MarkingKey markingKey(UUID actorId,UUID questionVersionId,UUID groupVersionId) {
        var version=versions.findById(questionVersionId).orElseThrow(()->new Unavailable(false));
        var group=version.getGroupVersion();
        if(!Objects.equals(groupVersionId,group==null?null:group.getGroupVersionId()))throw new Unavailable(false);
        var key=version.answerKey();
        if(key.correctChoiceIds().size()!=1)throw new Unavailable(false);
        var part=version.getQuestion().getPart();
        return new MarkingKey(version.getQuestion().getQuestionId(),questionVersionId,part.getPartId(),part.getPartNumber(),
                key.correctChoiceIds().iterator().next(),key.explanation());
    }
    public record MarkingKey(UUID questionId,UUID questionVersionId,int partId,int partNumber,UUID correctChoiceId,String explanation) {
        @Override public String toString() { return "MarkingKey[redacted]"; }
    }
}
