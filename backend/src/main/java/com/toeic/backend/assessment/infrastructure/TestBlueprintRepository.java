package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.application.ExamDraftException;
import com.toeic.backend.assessment.domain.TestBlueprintSnapshot;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** Blueprint chỉ tạo version mới; không cập nhật nội dung đã được đề/attempt pin. */
@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class TestBlueprintRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public TestBlueprintRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public List<TestBlueprintSnapshot> list(){return jdbc.query("SELECT rules::text FROM test_blueprints WHERE status='ACTIVE' AND blueprint_type='FULL' AND rules IS NOT NULL ORDER BY code,version_number",(r,n)->json.readValue(r.getString(1),TestBlueprintSnapshot.class));}
    public TestBlueprintSnapshot active(UUID id){
        var b=jdbc.query("SELECT rules::text FROM test_blueprints WHERE blueprint_id=? AND status='ACTIVE' AND blueprint_type='FULL' AND rules IS NOT NULL FOR SHARE",(r,n)->json.readValue(r.getString(1),TestBlueprintSnapshot.class),id)
                .stream().findFirst().orElseThrow(()->ExamDraftException.conflict("BLUEPRINT_NOT_AVAILABLE"));
        if(!id.equals(b.blueprintId()))throw ExamDraftException.conflict("BLUEPRINT_INVALID");
        return b;
    }
    public void create(TestBlueprintSnapshot blueprint){
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM test_blueprints WHERE code=? AND version_number=?)",Boolean.class,blueprint.code(),blueprint.version())))throw ExamDraftException.conflict("BLUEPRINT_VERSION_EXISTS");
        if(jdbc.queryForObject("SELECT count(*) FROM toeic_parts WHERE part_number BETWEEN 1 AND 7",Integer.class)!=7)throw ExamDraftException.conflict("PART_CATALOG_INCOMPLETE");
        jdbc.update("INSERT INTO test_blueprints(blueprint_id,code,version_number,name,blueprint_type,status,expected_total_questions,duration_seconds,rules,created_at,updated_at) VALUES(?,?,?,?,'FULL','ACTIVE',?,?,?::jsonb,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                blueprint.blueprintId(),blueprint.code(),blueprint.version(),blueprint.name(),blueprint.totalQuestions(),blueprint.durationSeconds(),json.writeValueAsString(blueprint));
        for(var part:blueprint.parts())jdbc.update("INSERT INTO test_blueprint_items(blueprint_item_id,blueprint_id,part_id,expected_question_count,expected_group_count) SELECT ?,?,part_id,?,? FROM toeic_parts WHERE part_number=?",
                UUID.randomUUID(),blueprint.blueprintId(),part.questionCount(),part.groupCount(),part.partNumber());
    }
    public void verifyItems(TestBlueprintSnapshot b){
        boolean consistent=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM test_blueprints WHERE blueprint_id=? AND code=? AND version_number=? AND name=? AND duration_seconds=? AND expected_total_questions=?)",Boolean.class,b.blueprintId(),b.code(),b.version(),b.name(),b.durationSeconds(),b.totalQuestions()));
        if(!consistent)throw ExamDraftException.conflict("BLUEPRINT_INVALID");
        var rows=jdbc.queryForList("SELECT p.part_number,i.expected_question_count,i.expected_group_count FROM test_blueprint_items i JOIN toeic_parts p USING(part_id) WHERE i.blueprint_id=? ORDER BY p.part_number",b.blueprintId());
        if(rows.size()!=7)throw ExamDraftException.conflict("BLUEPRINT_INVALID");
        for(int i=0;i<7;i++){
            var row=rows.get(i);var part=b.parts().get(i);
            if(!Objects.equals(row.get("part_number"),part.partNumber())||!Objects.equals(row.get("expected_question_count"),part.questionCount())||!Objects.equals(row.get("expected_group_count"),part.groupCount()))throw ExamDraftException.conflict("BLUEPRINT_INVALID");
        }
    }
}
