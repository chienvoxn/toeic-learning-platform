package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.application.*;
import com.toeic.backend.questionbank.domain.QuestionChoiceDraft;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Authoring writes only; shared Q-01 JPA repositories continue to read immutable snapshots. */
@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class QuestionAuthoringRepository {
    public record Parent(UUID id,UUID owner,String status,UUID pointer,UUID groupId,Integer partId,Integer topicId,Integer typeId,
            String groupType,OffsetDateTime createdAt,OffsetDateTime updatedAt) {}
    public record Version(UUID id,UUID parent,int number,String status,UUID groupVersionId,String text,String extra,String difficulty,
            OffsetDateTime createdAt,OffsetDateTime publishedAt,OffsetDateTime archivedAt) {}
    public record Link(UUID mediaId,String transcript,int order) {}
    public record Media(UUID id,UUID owner,String visibility,String type) {}
    private final JdbcTemplate jdbc;
    public QuestionAuthoringRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    private static String table(boolean group) { return group?"question_groups":"questions"; }
    private static String id(boolean group) { return group?"group_id":"question_id"; }
    private static String versions(boolean group) { return group?"question_group_versions":"question_versions"; }
    private static String versionId(boolean group) { return group?"group_version_id":"question_version_id"; }
    private static String links(boolean group) { return group?"question_group_version_resources":"question_resources"; }
    private Parent parent(ResultSet r,boolean group) throws SQLException {
        return new Parent(r.getObject(id(group),UUID.class),r.getObject("created_by",UUID.class),r.getString("status"),
                r.getObject("current_published_version_id",UUID.class),group?null:r.getObject("group_id",UUID.class),
                group?null:(Integer)r.getObject("part_id"),group?null:(Integer)r.getObject("topic_id"),
                group?null:(Integer)r.getObject("question_type_id"),group?r.getString("group_type"):null,
                r.getObject("created_at",OffsetDateTime.class),r.getObject("updated_at",OffsetDateTime.class));
    }
    public Optional<Parent> parent(boolean group,UUID id,boolean lock) {
        return jdbc.query("select * from "+table(group)+" where "+id(group)+"=?"+(lock?" for update":""),
                (r,n)->parent(r,group),id).stream().findFirst();
    }
    private Version version(ResultSet r,boolean group) throws SQLException {
        return new Version(r.getObject(versionId(group),UUID.class),r.getObject(id(group),UUID.class),r.getInt("version_number"),
                r.getString("status"),group?null:r.getObject("group_version_id",UUID.class),
                r.getString(group?"instruction":"question_text"),r.getString(group?"passage_text":"explanation"),
                group?null:r.getString("difficulty"),r.getObject("created_at",OffsetDateTime.class),
                r.getObject("published_at",OffsetDateTime.class),r.getObject("archived_at",OffsetDateTime.class));
    }
    public List<Version> versions(boolean group,UUID parent,boolean lock) {
        return jdbc.query("select * from "+versions(group)+" where "+id(group)+"=? order by version_number"+(lock?" for update":""),
                (r,n)->version(r,group),parent);
    }
    public Optional<Version> version(boolean group,UUID id) {
        return jdbc.query("select * from "+versions(group)+" where "+versionId(group)+"=?",(r,n)->version(r,group),id)
                .stream().findFirst();
    }
    public List<Link> assets(boolean group,UUID version) {
        return jdbc.query("select resource_id,transcript,"+(group?"display_order":"0 as display_order")+" from "+links(group)
                +" where "+versionId(group)+"=? order by "+(group?"display_order,":"")+"resource_id",
                (r,n)->new Link(r.getObject("resource_id",UUID.class),r.getString("transcript"),r.getInt("display_order")),version);
    }
    public List<QuestionChoiceDraft> choices(UUID version) {
        return jdbc.query("select * from question_choices where question_version_id=? order by display_order",
                (r,n)->new QuestionChoiceDraft(r.getString("choice_label"),r.getString("content"),r.getBoolean("is_correct"),r.getInt("display_order")),version);
    }
    public int partNumber(Integer id) {
        if(id==null)throw QuestionAuthoringException.invalid();
        return jdbc.query("select part_number from toeic_parts where part_id=?",(r,n)->r.getInt(1),id)
                .stream().findFirst().orElseThrow(QuestionAuthoringException::invalid);
    }
    public int typeId(Integer id) {
        if(id==null) return jdbc.query("select question_type_id from question_types where code='SINGLE_CHOICE'",(r,n)->r.getInt(1))
                .stream().findFirst().orElseThrow(QuestionAuthoringException::invalid);
        var valid=jdbc.query("select code from question_types where question_type_id=?",(r,n)->r.getString(1),id);
        if(valid.size()!=1||!valid.getFirst().equals("SINGLE_CHOICE"))throw QuestionAuthoringException.invalid();
        return id;
    }
    public void topic(Integer id) {
        if(id!=null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from topics where topic_id=?)",Boolean.class,id)))
            throw QuestionAuthoringException.invalid();
    }
    public String typeCode(int id) { return jdbc.queryForObject("select code from question_types where question_type_id=?",String.class,id); }

    public void insertQuestion(UUID id,UUID actor,Integer part,Integer topic,Integer type,UUID group,OffsetDateTime now) {
        jdbc.update("""
                insert into questions(question_id,created_by,part_id,topic_id,question_type_id,group_id,status,created_at,updated_at)
                values (?,?,?,?,?,?,'DRAFT',?,?)
                """,id,actor,part,topic,type,group,now,now);
    }
    public void insertGroup(UUID id,UUID actor,String type,OffsetDateTime now) {
        jdbc.update("insert into question_groups(group_id,created_by,group_type,status,created_at,updated_at) values (?,?,?,'DRAFT',?,?)",
                id,actor,type,now,now);
    }
    public void updateQuestion(Parent parent,Integer part,Integer topic,Integer type,UUID group,OffsetDateTime now) {
        jdbc.update("update questions set part_id=?,topic_id=?,question_type_id=?,group_id=?,updated_at=? where question_id=?",
                part,topic,type,group,now,parent.id());
    }
    public void updateGroup(Parent parent,String type,OffsetDateTime now) {
        jdbc.update("update question_groups set group_type=?,updated_at=? where group_id=?",type,now,parent.id());
    }
    public void archive(boolean group,UUID id,OffsetDateTime now) {
        jdbc.update("update "+table(group)+" set status='ARCHIVED',updated_at=? where "+id(group)+"=? and status<>'ARCHIVED'",now,id);
    }
    public UUID insertVersion(boolean group,UUID parent,int number,UUID groupVersion,String text,String extra,String difficulty,OffsetDateTime now) {
        var id=UUID.randomUUID();
        if(group) jdbc.update("""
                insert into question_group_versions(group_version_id,group_id,version_number,status,instruction,passage_text,created_at)
                values (?,?,?,'DRAFT',?,?,?)
                """,id,parent,number,text,extra,now);
        else jdbc.update("""
                insert into question_versions(question_version_id,question_id,version_number,status,group_version_id,question_text,explanation,difficulty,created_at)
                values (?,?,?,'DRAFT',?,?,?,?,?)
                """,id,parent,number,groupVersion,text,extra,difficulty,now);
        return id;
    }
    public void editVersion(boolean group,UUID id,UUID groupVersion,String text,String extra,String difficulty) {
        int changed=group?jdbc.update("update question_group_versions set instruction=?,passage_text=? where group_version_id=? and status='DRAFT'",text,extra,id)
                :jdbc.update("update question_versions set group_version_id=?,question_text=?,explanation=?,difficulty=? where question_version_id=? and status='DRAFT'",
                        groupVersion,text,extra,difficulty,id);
        if(changed!=1)throw QuestionAuthoringException.conflict();
    }
    private void requireDraft(boolean group,UUID version) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from "+versions(group)+" where "+versionId(group)+"=? and status='DRAFT' and published_at is null and archived_at is null)",Boolean.class,version)))
            throw QuestionAuthoringException.conflict();
    }
    public void replaceChoices(UUID version,List<QuestionChoiceDraft> choices) {
        requireDraft(false,version);
        jdbc.update("delete from question_choices where question_version_id=?",version);
        for(var choice:choices) jdbc.update("""
                insert into question_choices(choice_id,question_version_id,choice_label,content,is_correct,display_order) values (?,?,?,?,?,?)
                """,UUID.randomUUID(),version,choice.label(),choice.content(),choice.correct(),choice.displayOrder());
    }
    public void replaceAssets(boolean group,UUID version,List<Link> assets) {
        requireDraft(group,version);
        jdbc.update("delete from "+links(group)+" where "+versionId(group)+"=?",version);
        for(var asset:assets) {
            if(group) jdbc.update("insert into question_group_version_resources(group_version_id,resource_id,display_order,transcript) values (?,?,?,?)",
                    version,asset.mediaId(),asset.order(),asset.transcript());
            else jdbc.update("insert into question_resources(question_version_id,resource_id,transcript) values (?,?,?)",version,asset.mediaId(),asset.transcript());
        }
    }
    public Optional<Media> lockMedia(UUID id) {
        return jdbc.query("select resource_id,owner_id,visibility,resource_type from media_resources where resource_id=? for share",
                (r,n)->new Media(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4)),id).stream().findFirst();
    }
    public boolean lockGrant(UUID media,UUID actor) {
        return jdbc.query("select revoked_at is null from media_reuse_grants where media_id=? and user_id=? for share",
                (r,n)->r.getBoolean(1),media,actor).stream().findFirst().orElse(false);
    }
    public List<QuestionAuthoringDtos.Asset> readableAssets(boolean group,UUID version,boolean readMember) {
        return jdbc.query("""
                select m.resource_id,m.resource_type,m.mime_type,m.file_size,m.duration_seconds,r.transcript,
                """+(group?"r.display_order":"null::integer as display_order")+" from "+links(group)+" r join media_resources m using(resource_id) where r."+versionId(group)
                +"=? and (m.visibility='PUBLIC' or (m.visibility='AUTHENTICATED' and ?)) order by "+(group?"r.display_order,":"")+"m.resource_id",
                (r,n)->new QuestionAuthoringDtos.Asset(r.getObject("resource_id",UUID.class),r.getString("resource_type"),r.getString("mime_type"),
                        r.getObject("file_size")==null?null:((Number)r.getObject("file_size")).longValue(),(Integer)r.getObject("duration_seconds"),
                        (Integer)r.getObject("display_order"),"AUDIO".equals(r.getString("resource_type"))?r.getString("transcript"):null),version,readMember);
    }
    public QuestionAuthoringDtos.QuestionSummary questionSummary(Parent p) {
        return new QuestionAuthoringDtos.QuestionSummary(p.id(),p.partId(),p.topicId(),typeCode(p.typeId()),p.groupId(),p.status(),p.pointer(),p.createdAt(),p.updatedAt());
    }
    public QuestionAuthoringDtos.GroupSummary groupSummary(Parent p) {
        return new QuestionAuthoringDtos.GroupSummary(p.id(),p.groupType(),p.status(),p.pointer(),p.createdAt(),p.updatedAt());
    }
    public QuestionAuthoringDtos.Page<Parent> list(boolean group,UUID actor,boolean any,QuestionAuthoringQuery query) {
        var fields=Map.of("createdAt","p.created_at","updatedAt","p.updated_at","status","p.status",group?"groupId":"questionId","p."+id(group));
        if(!fields.containsKey(query.sort())||!Set.of("asc","desc").contains(query.direction())||query.page()<0||query.size()<1||query.size()>100
                ||(long)query.page()*query.size()>Integer.MAX_VALUE)throw QuestionAuthoringException.invalid();
        var args=new ArrayList<Object>();
        var where=new StringBuilder(" where (? or (p.created_by=?");
        args.add(any);args.add(actor);
        if(!group)where.append(" and (p.group_id is null or g.created_by=?)");
        if(!group)args.add(actor);
        where.append("))");
        if(query.status()!=null){where.append(" and p.status=?");args.add(query.status());}
        if(!group&&query.partId()!=null){where.append(" and p.part_id=?");args.add(query.partId());}
        if(!group&&query.topicId()!=null){where.append(" and p.topic_id=?");args.add(query.topicId());}
        if(group&&query.groupType()!=null){where.append(" and p.group_type=?");args.add(query.groupType());}
        var from=" from "+table(group)+" p"+(group?"":" left join question_groups g on g.group_id=p.group_id");
        long count=jdbc.queryForObject("select count(*)"+from+where,Long.class,args.toArray());
        String order=fields.get(query.sort())+" "+query.direction()+", p."+id(group)+" asc";
        args.add(query.size());args.add(query.page()*query.size());
        var data=jdbc.query("select p.*"+from+where+" order by "+order+" limit ? offset ?",(r,n)->parent(r,group),args.toArray());
        return new QuestionAuthoringDtos.Page<>(data,query.page(),query.size(),count,(int)((count+query.size()-1)/query.size()));
    }
}
