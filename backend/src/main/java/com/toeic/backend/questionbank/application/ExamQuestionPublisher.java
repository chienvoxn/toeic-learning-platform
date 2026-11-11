package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.QuestionChoiceDraft;
import com.toeic.backend.questionbank.infrastructure.QuestionAuthoringRepository;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Chuyển bản đề đã được kiểm tra/duyệt thành các version ngân hàng bất biến. */
@Service
public class ExamQuestionPublisher {
    private final QuestionAuthoringRepository authoring;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public ExamQuestionPublisher(QuestionAuthoringRepository authoring,JdbcTemplate jdbc,Clock clock) { this.authoring=authoring;this.jdbc=jdbc;this.clock=clock; }
    public record Pin(UUID versionId,UUID groupVersionId,int partId,int order) {}
    private List<QuestionAuthoringRepository.Link> assets(UUID audio,UUID image,String transcript) {
        var links=new ArrayList<QuestionAuthoringRepository.Link>();
        if(audio!=null)links.add(new QuestionAuthoringRepository.Link(audio,transcript,1));
        if(image!=null)links.add(new QuestionAuthoringRepository.Link(image,null,links.size()+1));
        return links;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    @PreAuthorize("hasAuthority('test:publish') and @examAccess.actor(#actorId)")
    public List<Pin> publish(UUID actorId,UUID owner,ExamPublication doc) {
        var now=OffsetDateTime.now(clock);var groups=new HashMap<String,UUID[]>();
        for(var g:doc.groups()) {
            var id=UUID.randomUUID();authoring.insertGroup(id,owner,"PART_"+g.partNumber(),now);
            var v=authoring.insertVersion(true,id,1,null,g.instruction(),g.passageText(),null,now);
            authoring.replaceAssets(true,v,assets(g.audioId(),g.imageId(),g.transcript()));
            jdbc.update("update question_group_versions set status='PUBLISHED',published_at=? where group_version_id=?",now,v);
            jdbc.update("update question_groups set status='PUBLISHED',current_published_version_id=? where group_id=?",v,id);
            groups.put(g.key(),new UUID[]{id,v});
        }
        int type=authoring.typeId(null),order=0;var groupOrder=new HashMap<String,Integer>();var pins=new ArrayList<Pin>();
        for(var q:doc.questions()) {
            int part=jdbc.queryForObject("select part_id from toeic_parts where part_number=?",Integer.class,q.partNumber());
            var g=groups.get(q.groupKey());var id=UUID.randomUUID();
            authoring.insertQuestion(id,owner,part,null,type,g==null?null:g[0],now);
            var v=authoring.insertVersion(false,id,1,g==null?null:g[1],q.questionText(),q.explanation(),null,now);
            var choices=new ArrayList<QuestionChoiceDraft>();
            for(int i=0;i<q.choices().size();i++) { String label=String.valueOf((char)('A'+i)); choices.add(new QuestionChoiceDraft(label,q.choices().get(i),label.equals(q.answer()),i+1)); }
            authoring.replaceChoices(v,choices);authoring.replaceAssets(false,v,assets(q.audioId(),q.imageId(),q.transcript()));
            jdbc.update("update question_versions set status='PUBLISHED',reviewed_by=?,reviewed_at=?,approved_by=?,approved_at=?,published_at=?,group_question_order=? where question_version_id=?",actorId,now,actorId,now,now,g==null?null:groupOrder.merge(q.groupKey(),1,Integer::sum),v);
            jdbc.update("update questions set status='PUBLISHED',current_published_version_id=? where question_id=?",v,id);
            pins.add(new Pin(v,g==null?null:g[1],part,++order));
        }
        return pins;
    }
}
