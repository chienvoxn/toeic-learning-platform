package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.infrastructure.ExamRepository;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ghi đáp án, quản lý giờ server và chấm trong một transaction khóa attempt; hỗ trợ cả practice snapshot. */
@Service
@PreAuthorize("hasAnyAuthority('test:take','practice:manage:self') and @examAccess.actor(#actorId)")
public class ExamAttemptService {
    private final ExamRepository db;private final Clock clock;
    public ExamAttemptService(ExamRepository db,Clock clock){this.db=db;this.clock=clock;}
    private OffsetDateTime now(){return OffsetDateTime.now(clock);}
    private OffsetDateTime time(Object o){return o instanceof OffsetDateTime t?t:((java.sql.Timestamp)o).toInstant().atOffset(ZoneOffset.UTC);}
    private Map<String,Object> owned(UUID actor,UUID id){return db.one("select * from attempts where attempt_id=? and user_id=? for update",id,actor);}
    @Transactional
    @PreAuthorize("hasAuthority('test:take') and @examAccess.actor(#actorId)")
    public Map<String,Object> start(UUID actorId,UUID versionId,UUID startKey) {
        if(startKey==null)throw ExamException.invalid("Cần startKey để chống tạo bài trùng khi thử lại.");
        db.one("select user_id from users where user_id=? for update",actorId);
        var previous=db.rows("select * from attempts where user_id=? and start_key=?",actorId,startKey);
        if(!previous.isEmpty()){if(!versionId.equals(previous.getFirst().get("test_version_id")))throw ExamException.conflict("startKey đã dùng cho đề khác.");return get(actorId,(UUID)previous.getFirst().get("attempt_id"));}
        var v=db.one("select * from test_versions where test_version_id=? and status='PUBLISHED' and draft_document is not null for share",versionId);
        int count=((Number)db.one("select count(*) n from attempts where user_id=? and test_version_id=?",actorId,versionId).get("n")).intValue();
        if(v.get("max_attempts")!=null&&count>=(int)v.get("max_attempts"))throw ExamException.conflict("Bạn đã đạt giới hạn số lượt thi.");
        var id=UUID.randomUUID();var started=now();int duration=(int)v.get("duration_seconds");
        db.write("""
            insert into attempts(attempt_id,user_id,test_version_id,attempt_type,attempt_source_type,expires_at,max_attempts_snapshot,status,started_at,duration_seconds,total_questions,created_at,start_key,listening_ends_at,score_conversion_table_id)
            values(?,?,?,?,'TEST_VERSION',?,?,'IN_PROGRESS',?,?,?,?,?,?,?)
            """,id,actorId,versionId,v.get("test_type"),started.plusSeconds(duration),v.get("max_attempts"),started,duration,v.get("total_questions"),started,startKey,v.get("listening_seconds")==null?null:started.plusSeconds((int)v.get("listening_seconds")),v.get("conversion_id"));
        for(var q:db.rows("select * from test_questions where test_version_id=? order by question_order",versionId))db.write("insert into attempt_question_snapshots(snapshot_id,attempt_id,question_version_id,question_group_version_id,question_order,points,created_at) values(?,?,?,?,?,1,?)",UUID.randomUUID(),id,q.get("question_version_id"),q.get("question_group_version_id"),q.get("question_order"),started);
        db.event("AttemptStarted",id);return get(actorId,id);
    }
    private List<Map<String,Object>> questions(UUID id,boolean reveal) {
        var result=new ArrayList<Map<String,Object>>();
        for(var q:db.rows("""
            select s.question_order,s.question_version_id,s.question_group_version_id,qv.question_text,qv.explanation,p.part_number,p.part_id,
            g.passage_text,g.instruction,a.selected_choice_id,a.client_sequence
            from attempt_question_snapshots s join question_versions qv using(question_version_id)
            join questions q using(question_id) join toeic_parts p using(part_id)
            left join question_group_versions g on g.group_version_id=s.question_group_version_id
            left join attempt_answers a on a.attempt_id=s.attempt_id and a.question_version_id=s.question_version_id
            where s.attempt_id=? order by s.question_order
            """,id)) {
            var item=new LinkedHashMap<String,Object>();item.put("id",q.get("question_version_id"));item.put("orderIndex",q.get("question_order"));item.put("partNumber",q.get("part_number"));item.put("content",q.get("question_text"));item.put("passage",q.get("passage_text"));item.put("instruction",q.get("instruction"));item.put("groupVersionId",q.get("question_group_version_id"));
            var options=new ArrayList<Map<String,Object>>();String selected=null,correct=null;
            for(var choice:db.rows("select * from question_choices where question_version_id=? order by display_order",q.get("question_version_id"))) {
                options.add(Map.of("key",choice.get("choice_label"),"text",choice.get("content"),"choiceId",choice.get("choice_id")));
                if(choice.get("choice_id").equals(q.get("selected_choice_id")))selected=(String)choice.get("choice_label");
                if(Boolean.TRUE.equals(choice.get("is_correct")))correct=(String)choice.get("choice_label");
            }
            item.put("options",options);item.put("userAnswer",selected);item.put("clientSequence",q.get("client_sequence")==null?0:q.get("client_sequence"));
            var assets=new ArrayList<Map<String,Object>>();
            for(var asset:db.rows("""
                select m.resource_id,m.resource_type,r.transcript,'QUESTION' scope from question_resources r join media_resources m using(resource_id) where r.question_version_id=? and m.visibility in ('PUBLIC','AUTHENTICATED')
                union all select m.resource_id,m.resource_type,r.transcript,'GROUP' scope from question_group_version_resources r join media_resources m using(resource_id) where r.group_version_id=? and m.visibility in ('PUBLIC','AUTHENTICATED')
                """,q.get("question_version_id"),q.get("question_group_version_id"))) {
                var a=new LinkedHashMap<String,Object>();a.put("resourceId",asset.get("resource_id"));a.put("resourceType",asset.get("resource_type"));a.put("scope",asset.get("scope"));if(reveal)a.put("transcript",asset.get("transcript"));assets.add(a);
            }
            item.put("assets",assets);
            if(reveal){item.put("correctOption",correct);item.put("isCorrect",correct!=null&&correct.equals(selected));item.put("explanation",q.get("explanation"));}
            result.add(item);
        }
        return result;
    }
    @Transactional public Map<String,Object> get(UUID actorId,UUID id) {
        var a=owned(actorId,id);if("IN_PROGRESS".equals(a.get("status"))&&!now().isBefore(time(a.get("expires_at")))){grade(a);a=owned(actorId,id);}
        var view=new LinkedHashMap<String,Object>();view.put("id",id);view.put("status",a.get("status"));view.put("expiresAt",time(a.get("expires_at")));view.put("serverNow",now());view.put("listeningEndsAt",a.get("listening_ends_at")==null?null:time(a.get("listening_ends_at")));view.put("totalQuestions",a.get("total_questions"));view.put("durationSeconds",a.get("duration_seconds"));
        view.put("testTitle",a.get("test_version_id")==null?"Luyện tập theo Part":db.one("select title from test_versions where test_version_id=?",a.get("test_version_id")).get("title"));view.put("questions",questions(id,false));return view;
    }
    @Transactional public Map<String,Object> save(UUID actorId,UUID id,UUID questionId,String option,long sequence) {
        var a=owned(actorId,id);
        if(!"IN_PROGRESS".equals(a.get("status")))throw new ExamException(409,"ATTEMPT_CLOSED","Bài đã nộp, không thể sửa.");
        if(!now().isBefore(time(a.get("expires_at"))))throw new ExamException(409,"ATTEMPT_EXPIRED","Bài đã hết giờ.");
        if(sequence<1||sequence>9007199254740991L||option==null)throw ExamException.invalid("Đáp án hoặc sequence không hợp lệ.");
        var q=db.one("select p.part_number from attempt_question_snapshots s join question_versions v using(question_version_id) join questions q using(question_id) join toeic_parts p using(part_id) where s.attempt_id=? and s.question_version_id=?",id,questionId);
        if(a.get("listening_ends_at")!=null && ((int)q.get("part_number")<=4)!=now().isBefore(time(a.get("listening_ends_at"))))throw new ExamException(409,"SECTION_CLOSED","Câu hỏi không thuộc phần thi đang mở.");
        var choice=db.rows("select choice_id from question_choices where question_version_id=? and choice_label=?",questionId,option);
        if(choice.isEmpty())throw ExamException.invalid("Lựa chọn không thuộc câu hỏi.");
        var existing=db.rows("select * from attempt_answers where attempt_id=? and question_version_id=?",id,questionId);
        if(!existing.isEmpty()&&((Number)existing.getFirst().get("client_sequence")).longValue()>=sequence) {
            if(((Number)existing.getFirst().get("client_sequence")).longValue()==sequence&&Objects.equals(existing.getFirst().get("selected_choice_id"),choice.getFirst().get("choice_id")))return Map.of("confirmedOption",option,"serverSequence",sequence);
            throw new ExamException(409,"ANSWER_STALE","Đáp án đã được cập nhật ở phiên khác. Tải lại bài để đồng bộ.");
        }
        db.write("""
            insert into attempt_answers(attempt_answer_id,attempt_id,question_version_id,selected_choice_id,answered_at,updated_at,client_sequence)
            values(?,?,?,?,?,?,?) on conflict(attempt_id,question_version_id) do update set selected_choice_id=excluded.selected_choice_id,updated_at=excluded.updated_at,client_sequence=excluded.client_sequence
            """,UUID.randomUUID(),id,questionId,choice.getFirst().get("choice_id"),now(),now(),sequence);
        return Map.of("confirmedOption",option,"serverSequence",sequence);
    }
    @Transactional public Map<String,Object> submit(UUID actorId,UUID id){var a=owned(actorId,id);grade(a);return result(actorId,id);}
    @Transactional public Map<String,Object> result(UUID actorId,UUID id){
        var a=owned(actorId,id);if(!"GRADED".equals(a.get("status")))throw ExamException.conflict("Bài chưa được nộp.");
        Map<String,Object> result=db.parse(a.get("result_document"),Map.class);
        // Kết quả chấm bất biến; quyền xem media vẫn được kiểm lại ở mỗi lần đọc.
        var allowed=new HashMap<String,Boolean>();
        for(Object entry:(List<?>)result.get("questions")) {
            var q=(Map<String,Object>)entry;var assets=(List<Map<String,Object>>)q.get("assets");
            q.put("assets",assets.stream().filter(asset->allowed.computeIfAbsent(asset.get("resourceId").toString(),mediaId->!db.rows("select 1 from media_resources where resource_id=? and visibility in ('PUBLIC','AUTHENTICATED')",UUID.fromString(mediaId)).isEmpty())).toList());
        }
        return result;
    }
    private void grade(Map<String,Object> a) {
        if("GRADED".equals(a.get("status")))return;
        UUID id=(UUID)a.get("attempt_id");var questions=questions(id,true);
        if(questions.size()!=((Number)a.get("total_questions")).intValue()||questions.stream().anyMatch(q->q.get("correctOption")==null))throw ExamException.conflict("Snapshot câu hỏi không còn đầy đủ; không chấm bằng dữ liệu thay thế.");
        int correct=0,listening=0,reading=0;var parts=new ArrayList<Map<String,Object>>();
        db.write("update attempts set status='SUBMITTED',submitted_at=? where attempt_id=?",now(),id);db.event("AttemptSubmitted",id);
        for(int part=1;part<=7;part++){final int n=part;var qs=questions.stream().filter(q->Objects.equals(q.get("partNumber"),n)).toList();if(qs.isEmpty())continue;int c=(int)qs.stream().filter(q->Boolean.TRUE.equals(q.get("isCorrect"))).count();correct+=c;if(part<5)listening+=c;else reading+=c;
            parts.add(Map.of("partNumber",part,"totalQuestions",qs.size(),"correctCount",c));
            db.write("insert into attempt_part_results(attempt_part_result_id,attempt_id,part_id,total_questions,correct_count,accuracy) select ?,?,part_id,?,?,? from toeic_parts where part_number=?",UUID.randomUUID(),id,qs.size(),c,100.0*c/qs.size(),part);
        }
        db.write("update attempt_answers a set is_correct=c.is_correct from question_choices c where a.selected_choice_id=c.choice_id and a.attempt_id=?",id);
        Integer ls=score(a,"LISTENING",listening,questions.stream().filter(q->(int)q.get("partNumber")<5).count());
        Integer rs=score(a,"READING",reading,questions.stream().filter(q->(int)q.get("partNumber")>=5).count());
        var result=new LinkedHashMap<String,Object>();result.put("attemptId",id);result.put("status","GRADED");result.put("submittedAt",now());result.put("rawCorrect",correct);result.put("totalQuestions",questions.size());result.put("accuracyRate",questions.isEmpty()?0:Math.round(100.0*correct/questions.size()));result.put("estimatedListeningScore",ls);result.put("estimatedReadingScore",rs);result.put("estimatedTotalScore",ls!=null&&rs!=null?ls+rs:null);result.put("scoreLabel","Điểm ước tính, không phải điểm ETS chính thức");result.put("questions",questions);result.put("parts",parts);
        db.write("update attempts set status='GRADED',correct_count=?,listening_correct_count=?,reading_correct_count=?,listening_estimated_score=?,reading_estimated_score=?,total_estimated_score=?,result_document=?::jsonb where attempt_id=?",correct,listening,reading,ls,rs,ls!=null&&rs!=null?ls+rs:null,db.json(result),id);db.event("AttemptGraded",id);
    }
    private Integer score(Map<String,Object> a,String skill,int raw,long count){if(a.get("score_conversion_table_id")==null||count!=100)return null;var rows=db.rows("select estimated_score from score_conversion_rules where score_conversion_table_id=? and skill=? and correct_count=?",a.get("score_conversion_table_id"),skill,raw);return rows.isEmpty()?null:(Integer)rows.getFirst().get("estimated_score");}
    // Worker nội bộ không có principal; chỉ được chấm những bài đã hết hạn theo giờ server.
    @Transactional
    @PreAuthorize("permitAll()")
    public void expire(UUID id){for(var a:db.rows("select * from attempts where attempt_id=? and status='IN_PROGRESS' and expires_at<=? for update skip locked",id,now()))grade(a);}
    @Transactional public List<Map<String,Object>> history(UUID actorId,int page){if(page<0||page>100000)throw ExamException.invalid("Trang không hợp lệ.");return db.rows("select a.attempt_id,a.status,a.started_at,a.expires_at,a.total_questions,a.correct_count,a.listening_estimated_score,a.reading_estimated_score,a.total_estimated_score,v.title from attempts a left join test_versions v using(test_version_id) where a.user_id=? order by a.started_at desc,a.attempt_id limit 50 offset ?",actorId,page*50);}
    @Transactional public List<Map<String,Object>> progress(UUID actorId){return db.rows("select p.part_number,sum(r.total_questions) total_questions,sum(r.correct_count) correct_count,count(*) attempts from attempt_part_results r join attempts a using(attempt_id) join toeic_parts p using(part_id) where a.user_id=? group by p.part_number order by p.part_number",actorId);}
}
