package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.*;
import com.toeic.backend.assessment.infrastructure.*;
import com.toeic.backend.system.application.AssessmentPublication;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** Kiểm blueprint và mapping/media lúc publish/start; không repin bank hoặc gọi publication G1. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class ExamPublicationPolicy implements AssessmentPublication {
    private final JdbcTemplate jdbc;private final ExamReferencePolicy refs;private final TestBlueprintRepository blueprints;private final ObjectMapper json;
    public ExamPublicationPolicy(JdbcTemplate jdbc,ExamReferencePolicy refs,TestBlueprintRepository blueprints,ObjectMapper json){this.jdbc=jdbc;this.refs=refs;this.blueprints=blueprints;this.json=json;}
    public ExamDraftDocument document(UUID version){return jdbc.query("SELECT document::text FROM test_version_drafts WHERE test_version_id=?",(r,n)->json.readValue(r.getString(1),ExamDraftDocument.class),version).stream().findFirst().orElseThrow(()->ExamDraftException.conflict("TEST_DOCUMENT_REQUIRED"));}
    public TestBlueprintSnapshot draftBlueprint(UUID version){
        UUID id=jdbc.queryForObject("SELECT blueprint_id FROM test_versions WHERE test_version_id=?",UUID.class,version);
        if(id==null)throw ExamDraftException.conflict("BLUEPRINT_REQUIRED");var b=blueprints.active(id);TestBlueprintService.validate(b);blueprints.verifyItems(b);return b;
    }
    @Override public void validate(UUID version){validateWith(version,draftBlueprint(version));}
    @Override public void freeze(UUID version){
        var b=draftBlueprint(version);validateWith(version,b);
        jdbc.update("UPDATE test_versions SET blueprint_snapshot=?::jsonb,duration_seconds=?,total_questions=? WHERE test_version_id=?",json.writeValueAsString(b),b.durationSeconds(),b.totalQuestions(),version);
    }
    public TestBlueprintSnapshot publishedBlueprint(UUID version){
        String raw=jdbc.queryForObject("SELECT blueprint_snapshot::text FROM test_versions WHERE test_version_id=?",String.class,version);
        if(raw==null)throw ExamDraftException.conflict("BLUEPRINT_SNAPSHOT_REQUIRED");
        var b=json.readValue(raw,TestBlueprintSnapshot.class);TestBlueprintService.validate(b);
        boolean matches=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM test_versions WHERE test_version_id=? AND blueprint_id=? AND duration_seconds=? AND total_questions=?)",Boolean.class,version,b.blueprintId(),b.durationSeconds(),b.totalQuestions()));
        if(!matches)throw ExamDraftException.conflict("BLUEPRINT_SNAPSHOT_MISMATCH");return b;
    }
    public void validateWith(UUID version,TestBlueprintSnapshot blueprint){
        var doc=document(version);refs.validate(doc,false);
        if(!doc.testType().equals("FULL")||doc.sections().size()!=2||!doc.sections().getFirst().type().equals("LISTENING")||!doc.sections().getLast().type().equals("READING"))throw ExamDraftException.conflict("FULL_MOCK_STRUCTURE_REQUIRED");
        if(doc.durationSeconds()!=null&&doc.durationSeconds()!=blueprint.durationSeconds())throw ExamDraftException.conflict("BLUEPRINT_DURATION_MISMATCH");
        var byPart=new TreeMap<Integer,ExamDraftDocument.Part>();
        for(var s:doc.sections())for(var p:s.parts())byPart.put(p.partNumber(),p);
        if(byPart.size()!=7)throw ExamDraftException.conflict("BLUEPRINT_COUNTS_MISMATCH");
        var mapping=jdbc.queryForList("SELECT question_version_id,question_group_version_id,question_order FROM test_questions WHERE test_version_id=? ORDER BY question_order",version);
        int order=0;
        for(var rule:blueprint.parts()){
            var part=byPart.get(rule.partNumber());if(part==null||part.questions().size()!=rule.questionCount())throw ExamDraftException.conflict("BLUEPRINT_COUNTS_MISMATCH");
            var groups=new HashMap<UUID,Integer>();
            for(var q:part.questions()){
                if(order>=mapping.size())throw ExamDraftException.conflict("TEST_MAPPING_MISMATCH");var row=mapping.get(order++);
                if(!Objects.equals(row.get("question_version_id"),q.questionVersionId())||!Objects.equals(row.get("question_group_version_id"),q.groupVersionId())||!Objects.equals(row.get("question_order"),order))throw ExamDraftException.conflict("TEST_MAPPING_MISMATCH");
                if(q.groupVersionId()!=null)groups.merge(q.groupVersionId(),1,Integer::sum);
                var types=new HashSet<>(jdbc.query("""
                    SELECT m.resource_type FROM media_resources m WHERE m.visibility IN ('PUBLIC','AUTHENTICATED') AND
                    (EXISTS(SELECT 1 FROM question_resources r WHERE r.resource_id=m.resource_id AND r.question_version_id=?) OR
                     EXISTS(SELECT 1 FROM question_group_version_resources r WHERE r.resource_id=m.resource_id AND r.group_version_id=?))
                    """,(r,n)->r.getString(1),q.questionVersionId(),q.groupVersionId()));
                if(rule.requireAudio()&&!types.contains("AUDIO")||rule.requireImage()&&!types.contains("IMAGE"))throw ExamDraftException.conflict("TEST_REQUIRED_MEDIA_UNAVAILABLE");
            }
            if(groups.size()!=rule.groupCount())throw ExamDraftException.conflict("BLUEPRINT_GROUPS_MISMATCH");
            if(rule.minGroupSize()!=null&&groups.values().stream().anyMatch(n->n<rule.minGroupSize()||n>rule.maxGroupSize()))throw ExamDraftException.conflict("BLUEPRINT_GROUPS_MISMATCH");
        }
        if(order!=mapping.size())throw ExamDraftException.conflict("TEST_MAPPING_MISMATCH");
    }
}
