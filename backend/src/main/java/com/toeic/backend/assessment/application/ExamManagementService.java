package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.ExamDocument;
import com.toeic.backend.assessment.infrastructure.ExamRepository;
import com.toeic.backend.questionbank.application.ExamQuestionPublisher;
import com.toeic.backend.questionbank.application.ExamPublication;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Quản lý nháp, kiểm tra import, duyệt và xuất bản; không thay đổi version đã xuất bản. */
@Service
@PreAuthorize("hasAnyAuthority('test:manage:self','test:manage:any') and @examAccess.actor(#actorId)")
public class ExamManagementService {
    private final ExamRepository db; private final ExamValidator validator; private final ExamAccess access;
    private final ExamQuestionPublisher publisher; private final Clock clock;
    public ExamManagementService(ExamRepository db,ExamValidator validator,ExamAccess access,ExamQuestionPublisher publisher,Clock clock) { this.db=db;this.validator=validator;this.access=access;this.publisher=publisher;this.clock=clock; }
    private Map<String,Object> owned(UUID actor,UUID id) {
        var row=db.one("select v.*,t.created_by from test_versions v join tests t on t.test_id=v.test_id where v.test_version_id=? for update of v",id);
        if(!access.has("test:manage:any")&&!actor.equals(row.get("created_by")))throw ExamException.missing(); return row;
    }
    public record Draft(UUID versionId,UUID testId,int version,int revision,String status,String reviewStatus,String reviewNote,ExamDocument document) {}
    private Draft view(Map<String,Object> row) { return new Draft((UUID)row.get("test_version_id"),(UUID)row.get("test_id"),(int)row.get("version_number"),(int)row.get("revision"),(String)row.get("status"),(String)row.get("review_status"),(String)row.get("review_note"),db.parse(row.get("draft_document"),ExamDocument.class)); }
    public record Summary(UUID versionId,int version,String status,String reviewStatus,String title,int questionCount) {}
    @Transactional(readOnly=true) public List<Summary> list(UUID actorId,int page) {
        if(page<0||page>100000)throw ExamException.invalid("Trang không hợp lệ.");
        return db.rows("select v.test_version_id,v.version_number,v.status,v.review_status,v.draft_document->>'title' draft_title,jsonb_array_length(v.draft_document->'questions') question_count from test_versions v join tests t on t.test_id=v.test_id where v.draft_document is not null and (? or t.created_by=?) order by v.created_at desc,v.test_version_id limit 50 offset ?",access.has("test:manage:any"),actorId,page*50).stream().map(r->new Summary((UUID)r.get("test_version_id"),(int)r.get("version_number"),(String)r.get("status"),(String)r.get("review_status"),(String)r.get("draft_title"),(int)r.get("question_count"))).toList();
    }
    @Transactional public Draft get(UUID actorId,UUID id) { return view(owned(actorId,id)); }
    private void basic(ExamDocument d) {
        if(d==null||d.title()==null||d.title().isBlank()||d.title().length()>255||d.testType()==null||!Set.of("PRACTICE","LISTENING","READING","FULL").contains(d.testType())||d.durationSeconds()==null||d.questions()==null||d.questions().size()>200||db.json(d).length()>2_000_000)throw ExamException.invalid("Tên, loại đề hoặc kích thước bản nháp không hợp lệ.");
        if(d.questions().stream().anyMatch(q->q==null||q.questionText()==null||q.choices()==null||q.choices().size()>4||q.choices().stream().anyMatch(Objects::isNull)) || d.groups()!=null&&d.groups().stream().anyMatch(g->g==null||g.key()==null))throw ExamException.invalid("Cấu trúc câu hỏi/nhóm không hợp lệ; nội dung đang soạn có thể để chuỗi trống.");
    }
    @Transactional public Draft create(UUID actorId,ExamDocument doc) {
        basic(doc);UUID test=UUID.randomUUID(),v=UUID.randomUUID();var now=OffsetDateTime.now(clock);
        db.write("insert into tests(test_id,created_by,title,description,test_type,status,created_at,updated_at) values(?,?,?,?,?,'DRAFT',?,?)",test,actorId,doc.title(),doc.description(),doc.testType(),now,now);
        db.write("insert into test_versions(test_version_id,test_id,version_number,status,created_at,draft_document) values(?,?,1,'DRAFT',?,?::jsonb)",v,test,now,db.json(doc));db.audit(actorId,"EXAM_CREATED",v);return get(actorId,v);
    }
    @Transactional public Draft save(UUID actorId,UUID id,int revision,ExamDocument doc) {
        basic(doc);var row=owned(actorId,id);editable(row,revision);
        db.write("update test_versions set draft_document=?::jsonb,revision=revision+1,review_note=null where test_version_id=?",db.json(doc),id);return get(actorId,id);
    }
    private void editable(Map<String,Object> row,int revision) {
        if(!"DRAFT".equals(row.get("status"))||!"DRAFT".equals(row.get("review_status")))throw ExamException.conflict("Đề đang duyệt hoặc đã xuất bản; không thể sửa.");
        if(!Objects.equals(revision,row.get("revision")))throw ExamException.conflict("Bản nháp đã thay đổi. Tải lại trước khi lưu.");
    }
    @Transactional public List<ExamException.Issue> validate(UUID actorId,ExamDocument doc) { return check(doc,actorId); }
    private List<ExamException.Issue> check(ExamDocument doc,UUID owner) {
        var errors=new ArrayList<>(validator.validate(doc));if(doc==null)return errors;
        if(doc.groups()!=null)for(int i=0;i<doc.groups().size();i++){var g=doc.groups().get(i);if(g!=null){media(errors,owner,g.audioId(),"AUDIO","groups["+i+"].audioId");media(errors,owner,g.imageId(),"IMAGE","groups["+i+"].imageId");}}
        if(doc.questions()!=null)for(int i=0;i<doc.questions().size();i++){var q=doc.questions().get(i);if(q!=null){media(errors,owner,q.audioId(),"AUDIO","questions["+i+"].audioId");media(errors,owner,q.imageId(),"IMAGE","questions["+i+"].imageId");}}
        if(doc.conversionId()!=null && db.rows("select 1 from score_conversion_tables where score_conversion_table_id=? and status='ACTIVE'",doc.conversionId()).isEmpty())errors.add(new ExamException.Issue("conversionId","Bảng điểm không khả dụng."));
        return errors;
    }
    private void media(List<ExamException.Issue> errors,UUID owner,UUID id,String type,String field) {
        if(id==null)return;
        if(db.rows("select 1 from media_resources m join media_uploads u using(resource_id) where m.resource_id=? and m.resource_type=? and m.visibility in ('PUBLIC','AUTHENTICATED') and (m.owner_id=? or exists(select 1 from media_reuse_grants g where g.media_id=m.resource_id and g.user_id=? and g.revoked_at is null)) for share of m",id,type,owner,owner).isEmpty())errors.add(new ExamException.Issue(field,"Media không tồn tại, sai loại hoặc chưa có quyền sử dụng."));
    }
    @Transactional public Draft submit(UUID actorId,UUID id,int revision) {
        var row=owned(actorId,id);editable(row,revision);var errors=check(view(row).document(),(UUID)row.get("created_by"));
        if(!errors.isEmpty())throw new ExamException(422,"EXAM_INVALID","Đề còn lỗi cần sửa.",errors);
        db.write("update test_versions set review_status='IN_REVIEW',revision=revision+1 where test_version_id=?",id);db.audit(actorId,"EXAM_REVIEW_REQUESTED",id);return get(actorId,id);
    }
    @Transactional
    @PreAuthorize("hasAuthority('test:publish') and hasAuthority('test:manage:any') and @examAccess.actor(#actorId)")
    public Draft review(UUID actorId,UUID id,int revision,String decision,String note) {
        var row=owned(actorId,id);
        if(!"IN_REVIEW".equals(row.get("review_status"))||!"DRAFT".equals(row.get("status"))||!Objects.equals(revision,row.get("revision")))throw ExamException.conflict("Đề không còn ở phiên bản chờ duyệt này.");
        if(!Set.of("APPROVE","RETURN").contains(Objects.toString(decision,"")))throw ExamException.invalid("Quyết định không hợp lệ.");
        if(note!=null&&note.length()>2000)throw ExamException.invalid("Ghi chú tối đa 2000 ký tự.");
        db.write("update test_versions set review_status=?,reviewed_by=?,review_note=?,revision=revision+1 where test_version_id=?","APPROVE".equals(decision)?"APPROVED":"DRAFT",actorId,note,id);db.audit(actorId,"EXAM_"+decision,id);return get(actorId,id);
    }
    @Transactional
    @PreAuthorize("hasAuthority('test:publish') and hasAuthority('test:manage:any') and @examAccess.actor(#actorId)")
    public Draft publish(UUID actorId,UUID id,int revision) {
        var row=owned(actorId,id);
        if(!"DRAFT".equals(row.get("status"))||!"APPROVED".equals(row.get("review_status"))||!Objects.equals(revision,row.get("revision")))throw ExamException.conflict("Cần duyệt đúng phiên bản trước khi xuất bản.");
        var doc=view(row).document();var owner=(UUID)row.get("created_by");var errors=check(doc,owner);
        if(!errors.isEmpty())throw new ExamException(422,"EXAM_INVALID","Đề chưa đủ điều kiện xuất bản.",errors);
        var publication=new ExamPublication((doc.groups()==null?List.<ExamDocument.Group>of():doc.groups()).stream().map(g->new ExamPublication.Group(g.key(),g.partNumber(),g.instruction(),g.passageText(),g.audioId(),g.imageId(),g.transcript())).toList(),doc.questions().stream().map(q->new ExamPublication.Question(q.partNumber(),q.groupKey(),q.questionText(),q.choices(),q.answer(),q.explanation(),q.audioId(),q.imageId(),q.transcript())).toList());
        var now=OffsetDateTime.now(clock);var pins=publisher.publish(actorId,owner,publication);var blueprint=UUID.randomUUID();
        db.write("insert into test_blueprints(blueprint_id,code,version_number,name,blueprint_type,status,expected_total_questions,created_at,updated_at) values(?,?,1,?,?,'ACTIVE',?,?,?)",blueprint,id.toString(),doc.title().substring(0,Math.min(150,doc.title().length())),doc.testType(),pins.size(),now,now);
        var parts=new TreeSet<Integer>();pins.forEach(p->parts.add(p.partId()));
        for(int part:parts){var pp=pins.stream().filter(p->p.partId()==part).toList();db.write("insert into test_blueprint_items values(?,?,?,?,?)",UUID.randomUUID(),blueprint,part,pp.size(),pp.stream().map(ExamQuestionPublisher.Pin::groupVersionId).filter(Objects::nonNull).distinct().count());}
        for(var pin:pins)db.write("insert into test_questions(test_version_id,question_version_id,question_group_version_id,question_order,points) values(?,?,?,?,1)",id,pin.versionId(),pin.groupVersionId(),pin.order());
        db.write("update test_versions set status='PUBLISHED',published_at=?,title=?,description=?,test_type=?,duration_seconds=?,max_attempts=?,total_questions=?,blueprint_id=?,listening_seconds=?,conversion_id=?,revision=revision+1 where test_version_id=?",now,doc.title(),doc.description(),doc.testType(),doc.durationSeconds(),doc.maxAttempts(),pins.size(),blueprint,"FULL".equals(doc.testType())?2700:null,doc.conversionId(),id);
        db.write("update tests set status='PUBLISHED',updated_at=? where test_id=?",now,row.get("test_id"));db.audit(actorId,"EXAM_PUBLISHED",id);return get(actorId,id);
    }
    @Transactional public Draft newVersion(UUID actorId,UUID id) {
        var row=owned(actorId,id);var test=(UUID)row.get("test_id");db.one("select test_id from tests where test_id=? for update",test);
        var drafts=db.rows("select * from test_versions where test_id=? and status='DRAFT'",test);if(!drafts.isEmpty())return view(drafts.getFirst());
        var v=UUID.randomUUID();int n=((Number)db.one("select max(version_number) n from test_versions where test_id=?",test).get("n")).intValue()+1;
        db.write("insert into test_versions(test_version_id,test_id,version_number,status,created_at,draft_document) values(?,?,?,'DRAFT',?,?::jsonb)",v,test,n,OffsetDateTime.now(clock),row.get("draft_document").toString());db.audit(actorId,"EXAM_VERSION_CREATED",v);return get(actorId,v);
    }
    @Transactional
    @PreAuthorize("hasAuthority('test:publish') and hasAuthority('test:manage:any') and @examAccess.actor(#actorId)")
    public Draft retire(UUID actorId,UUID id) { var row=owned(actorId,id);if(!"PUBLISHED".equals(row.get("status")))throw ExamException.conflict("Chỉ ngừng phát hành đề đã xuất bản.");db.write("update test_versions set status='RETIRED',retired_at=? where test_version_id=?",OffsetDateTime.now(clock),id);db.audit(actorId,"EXAM_RETIRED",id);return get(actorId,id); }
}
