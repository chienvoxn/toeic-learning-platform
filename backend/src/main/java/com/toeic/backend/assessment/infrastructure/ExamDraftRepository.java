package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.domain.ExamDraftDocument;
import com.toeic.backend.assessment.application.ExamDraftException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** Lưu DRAFT và các FK version trong transaction của service; khóa parent trước version. */
@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class ExamDraftRepository {
    public record Draft(UUID testId, UUID versionId, UUID ownerId, int version, int revision, String status,
                        ExamDraftDocument document) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ExamDraftRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public boolean owns(UUID test, UUID actor, boolean any, boolean lock) {
        return !jdbc.query("select test_id from tests where test_id=? and (created_by=? or ?)"+(lock?" for update":""),
                (r,n)->r.getObject(1),test,actor,any).isEmpty();
    }
    public List<Map<String,Object>> list(UUID actor, boolean any, int page) {
        return jdbc.queryForList("""
            select test_id as "testId", title, status, test_type as "testType"
            from tests where created_by=? or ? order by created_at desc,test_id limit 50 offset ?
            """,actor,any,(long)page*50);
    }
    public Optional<Draft> find(UUID test, UUID version, boolean lock) {
        return jdbc.query("""
            select t.created_by,v.test_id,v.test_version_id,v.version_number,v.status,d.revision,d.document
            from test_versions v join tests t on t.test_id=v.test_id
            left join test_version_drafts d on d.test_version_id=v.test_version_id
            where v.test_id=? and (?::uuid is null or v.test_version_id=?)
            order by v.version_number desc limit 1
            """+(lock?" for update of v":""),(r,n)->new Draft(r.getObject("test_id",UUID.class),
                r.getObject("test_version_id",UUID.class),r.getObject("created_by",UUID.class),r.getInt("version_number"),
                r.getInt("revision"),r.getString("status"),r.getString("document")==null?null:json.readValue(r.getString("document"),ExamDraftDocument.class)),
                test,version,version).stream().findFirst();
    }
    public boolean pinned(UUID version) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from attempts where test_version_id=?)",Boolean.class,version));
    }
    public boolean working(UUID test){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM test_versions WHERE test_id=? AND status IN ('DRAFT','IN_REVIEW','APPROVED'))",Boolean.class,test));}
    public List<Map<String,Object>> versions(UUID test){return jdbc.queryForList("SELECT v.test_version_id AS \"versionId\",v.version_number AS version,v.status,d.revision,m.source_version_id AS \"sourceVersionId\" FROM test_versions v LEFT JOIN test_version_drafts d USING(test_version_id) LEFT JOIN moderation_versions m ON m.kind='tests' AND m.version_id=v.test_version_id WHERE v.test_id=? ORDER BY v.version_number DESC",test);}
    public UUID blueprint(UUID version){return jdbc.queryForObject("SELECT blueprint_id FROM test_versions WHERE test_version_id=?",UUID.class,version);}
    public void selectBlueprint(UUID version,UUID blueprint){jdbc.update("UPDATE test_versions SET blueprint_id=? WHERE test_version_id=?",blueprint,version);jdbc.update("UPDATE test_version_drafts SET revision=revision+1 WHERE test_version_id=?",version);}
    public Draft fork(Draft source,UUID actor){
        int next=jdbc.queryForObject("SELECT coalesce(max(version_number),0)+1 FROM test_versions WHERE test_id=?",Integer.class,source.testId());UUID version=UUID.randomUUID();
        jdbc.update("INSERT INTO test_versions(test_version_id,test_id,version_number,status,created_at,blueprint_id,duration_seconds,total_questions) SELECT ?,test_id,?,'DRAFT',CURRENT_TIMESTAMP,blueprint_id,duration_seconds,total_questions FROM test_versions WHERE test_version_id=?",version,next,source.versionId());
        jdbc.update("INSERT INTO test_version_drafts(test_version_id,revision,document) SELECT ?,0,document FROM test_version_drafts WHERE test_version_id=?",version,source.versionId());
        jdbc.update("INSERT INTO test_questions(test_version_id,question_version_id,question_group_version_id,question_order,points) SELECT ?,question_version_id,question_group_version_id,question_order,points FROM test_questions WHERE test_version_id=?",version,source.versionId());
        return find(source.testId(),version,false).orElseThrow(ExamDraftException::missing);
    }
    public void create(UUID actor, UUID test, UUID version, ExamDraftDocument document, OffsetDateTime now) {
        jdbc.update("insert into tests(test_id,created_by,title,description,test_type,status,created_at,updated_at) values(?,?,?,?,?,'DRAFT',?,?)",
                test,actor,document.title(),document.description(),document.testType(),now,now);
        jdbc.update("insert into test_versions(test_version_id,test_id,version_number,status,created_at) values(?,?,1,'DRAFT',?)",version,test,now);
        jdbc.update("insert into test_version_drafts(test_version_id,revision,document) values(?,0,?::jsonb)",version,json.writeValueAsString(document));
        references(version,document);
    }
    public void update(Draft draft, ExamDraftDocument document, OffsetDateTime now) {
        jdbc.update("update test_version_drafts set revision=revision+1,document=?::jsonb where test_version_id=?",json.writeValueAsString(document),draft.versionId());
        jdbc.update("update tests set title=?,description=?,test_type=?,updated_at=? where test_id=?",document.title(),document.description(),document.testType(),now,draft.testId());
        jdbc.update("delete from test_questions where test_version_id=?",draft.versionId());
        references(draft.versionId(),document);
    }
    private void references(UUID version, ExamDraftDocument document) {
        int order=0;
        for(var section:document.sections()) for(var part:section.parts()) for(var q:part.questions())
            jdbc.update("insert into test_questions(test_version_id,question_version_id,question_group_version_id,question_order) values(?,?,?,?)",
                    version,q.questionVersionId(),q.groupVersionId(),++order);
        jdbc.update("update test_versions set duration_seconds=?,total_questions=? where test_version_id=?",document.durationSeconds(),order,version);
    }
}
