package com.toeic.backend.system.infrastructure;

import com.toeic.backend.system.application.ModerationException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Luong review dung lai parent/version hien co; bang moi chi luu revision, provenance va bang chung actor. */
@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class ModerationRepository {
    public enum Kind {
        contents("learning_contents","content_id","learning_content_versions","content_version_id","author_id","content"),
        questions("questions","question_id","question_versions","question_version_id","created_by","question"),
        groups("question_groups","group_id","question_group_versions","group_version_id","created_by","question"),
        tests("tests","test_id","test_versions","test_version_id","created_by","test");
        public final String parents,id,versions,version,owner,permission;
        Kind(String p,String i,String v,String vi,String o,String permission){parents=p;id=i;versions=v;version=vi;owner=o;this.permission=permission;}
        public static Kind parse(String value){try{return valueOf(value);}catch(IllegalArgumentException e){throw ModerationException.invalid();}}
    }
    public record Parent(UUID id,UUID owner,String status,UUID pointer,UUID groupId){}
    public record Version(UUID id,String status){}
    public record Evidence(UUID source,UUID cycle,long revision,Long approved,boolean complete){}
    private final JdbcTemplate jdbc;
    public ModerationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Parent parent(Kind k,UUID id,boolean lock){
        return jdbc.query("SELECT "+k.owner+",status,current_published_version_id"+(k==Kind.questions?",group_id":"")+" FROM "+k.parents+" WHERE "+k.id+"=?"+(lock?" FOR UPDATE":""),
            (r,n)->new Parent(id,r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),k==Kind.questions?r.getObject(4,UUID.class):null),id).stream().findFirst().orElseThrow(ModerationException::missing);
    }
    public Version version(Kind k,UUID parent,UUID id){return jdbc.query("SELECT status FROM "+k.versions+" WHERE "+k.id+"=? AND "+k.version+"=? FOR UPDATE",
        (r,n)->new Version(id,r.getString(1)),parent,id).stream().findFirst().orElseThrow(ModerationException::missing);}
    public Evidence evidence(Kind k,UUID version){return jdbc.query("SELECT source_version_id,cycle_key,revision,approved_revision,evidence_complete FROM moderation_versions WHERE kind=? AND version_id=?",
        (r,n)->new Evidence(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getLong(3),(Long)r.getObject(4),r.getBoolean(5)),k.name(),version).stream().findFirst().orElse(null);}
    public void record(Kind k,UUID parent,UUID version,UUID source,UUID actor,boolean created){
        var old=evidence(k,version);
        if(old==null)jdbc.update("INSERT INTO moderation_versions(kind,parent_id,version_id,source_version_id,cycle_key,revision,evidence_complete) VALUES (?,?,?,?,?,?,?)",
            k.name(),parent,version,source,source==null?parent:source,k==Kind.tests?jdbc.queryForObject("SELECT revision FROM test_version_drafts WHERE test_version_id=?",Integer.class,version):0,created);
        else jdbc.update("UPDATE moderation_versions SET revision=revision+1,approved_revision=null WHERE kind=? AND version_id=?",k.name(),version);
        contributor(k,version,actor,created?"CREATED":"EDITED");
    }
    public void contributor(Kind k,UUID version,UUID actor,String action){jdbc.update("INSERT INTO moderation_contributors VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),k.name(),version,actor,action);}
    public boolean complete(Kind k,UUID parent,Evidence evidence){return evidence!=null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT bool_and(evidence_complete) FROM moderation_versions WHERE kind=? AND parent_id=? AND cycle_key=?",Boolean.class,k.name(),parent,evidence.cycle()));}
    public boolean contributor(Kind k,UUID parent,Evidence e,UUID actor){return e!=null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM moderation_versions v JOIN moderation_contributors c USING(kind,version_id) WHERE v.kind=? AND v.parent_id=? AND v.cycle_key=? AND c.actor_id=?)",Boolean.class,k.name(),parent,e.cycle(),actor));}
    public void state(Kind k,UUID version,String state,Long approval){
        jdbc.update("UPDATE "+k.versions+" SET status=? WHERE "+k.version+"=?",state,version);
        jdbc.update("UPDATE moderation_versions SET revision=revision+1,approved_revision=? WHERE kind=? AND version_id=?",approval,k.name(),version);
        if(k==Kind.tests)jdbc.update("UPDATE test_version_drafts SET revision=revision+1 WHERE test_version_id=?",version);
    }
    public void publishedRevision(Kind k,UUID version){
        jdbc.update("UPDATE moderation_versions SET revision=revision+1 WHERE kind=? AND version_id=?",k.name(),version);
        if(k==Kind.tests)jdbc.update("UPDATE test_version_drafts SET revision=revision+1 WHERE test_version_id=?",version);
    }
    public void publish(Kind k,UUID parent,UUID version,String visibility){
        jdbc.update("UPDATE "+k.versions+" SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP WHERE "+k.version+"=?",version);
        jdbc.update("UPDATE "+k.parents+" SET status='PUBLISHED',current_published_version_id=?,updated_at=CURRENT_TIMESTAMP"+(k==Kind.contents?",visibility=?,published_at=CURRENT_TIMESTAMP":"")+" WHERE "+k.id+"=?",
            k==Kind.contents?new Object[]{version,visibility,parent}:new Object[]{version,parent});
    }
    public void decision(Kind k,UUID version,long revision,UUID actor,String action,String reason){jdbc.update("INSERT INTO moderation_decisions VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),k.name(),version,revision,actor,action,reason);}
    public Map<String,Object> payload(Kind k,UUID version){
        if(k==Kind.tests)return jdbc.queryForMap("SELECT d.document::text, v.blueprint_id AS \"blueprintId\",v.blueprint_snapshot::text AS \"blueprintSnapshot\" FROM test_version_drafts d JOIN test_versions v USING(test_version_id) WHERE d.test_version_id=?",version);
        var columns=k==Kind.contents?"title,summary,body":k==Kind.questions?"question_text,explanation,difficulty,group_version_id":"instruction,passage_text";
        var value=new LinkedHashMap<>(jdbc.queryForMap("SELECT "+columns+" FROM "+k.versions+" WHERE "+k.version+"=?",version));
        if(k==Kind.questions)value.put("choices",jdbc.queryForList("SELECT choice_label,content,is_correct,display_order FROM question_choices WHERE question_version_id=? ORDER BY display_order",version));
        return value;
    }
    public List<Map<String,Object>> decisions(Kind k,UUID version){return jdbc.queryForList("SELECT revision,action,reason,created_at FROM moderation_decisions WHERE kind=? AND version_id=? ORDER BY created_at,decision_id",k.name(),version);}
    public Map<String,Object> list(Kind k,UUID actor,boolean any,int page){
        String from=" FROM "+k.parents+" p JOIN "+k.versions+" v USING("+k.id+") LEFT JOIN moderation_versions m ON m.kind=? AND m.version_id=v."+k.version+" WHERE (? OR p."+k.owner+"=?)";
        long count=jdbc.queryForObject("SELECT count(*)"+from,Long.class,k.name(),any,actor);
        var rows=jdbc.queryForList("SELECT p."+k.id+" AS \"parentId\",v."+k.version+" AS \"versionId\",v.version_number AS \"versionNumber\",v.status,p.status AS \"parentStatus\",m.revision,m.evidence_complete AS \"evidenceComplete\""+from+" ORDER BY p.updated_at DESC,p."+k.id+",v.version_number DESC LIMIT 20 OFFSET ?",k.name(),any,actor,page*20);
        return Map.of("items",rows,"page",page,"size",20,"totalElements",count);
    }
}
