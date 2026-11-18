package com.toeic.backend.ai.infrastructure;

import com.toeic.backend.ai.application.ChatContracts.*;
import com.toeic.backend.ai.application.ChatFailure;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Lưu hội thoại, request và snapshot; các ghi nhiều bảng do application bao transaction. */
@Repository
public class ChatRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ChatRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    public JdbcTemplate jdbc() { return jdbc; }
    public String encode(Object value) { return json.writeValueAsString(value); }
    public Answer decode(String value) { return json.readValue(value,Answer.class); }
    public void own(UUID actor,UUID conversation) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from ai_conversations where conversation_id=? and user_id=? and deleted_at is null)",Boolean.class,conversation,actor)))
            throw ChatFailure.missing();
    }
    public List<Conversation> list(UUID actor,int page) {
        return jdbc.query("select * from ai_conversations where user_id=? and deleted_at is null order by updated_at desc,conversation_id limit 20 offset ?",
                (r,n)->new Conversation(r.getObject("conversation_id",UUID.class),r.getString("title"),r.getString("updated_at")),actor,page*20);
    }
    public List<Message> messages(UUID conversation,int page) {
        var rows=jdbc.query("""
                select m.*,s.response_json from ai_messages m left join ai_citation_snapshots s using(message_id)
                where conversation_id=? order by m.created_at desc,m.message_id desc limit 50 offset ?
                """,(r,n)->new Message(r.getObject("message_id",UUID.class),r.getString("role"),r.getString("content"),
                r.getString("created_at"),r.getString("response_json")==null?null:decode(r.getString("response_json"))),conversation,page*50);
        Collections.reverse(rows); return rows;
    }
    public List<Evidence> scope(boolean member) {
        return jdbc.query("""
                select i.document_id,c.content_id,v.content_version_id from ai_indexed_versions i
                join learning_content_versions v on v.content_version_id=i.content_version_id
                join learning_contents c on c.current_published_version_id=v.content_version_id and c.content_id=v.content_id
                where c.status='PUBLISHED' and v.status='PUBLISHED' and v.archived_at is null
                and v.published_at is not null and v.published_at<=now()
                and (c.visibility='PUBLIC' or (? and c.visibility='AUTHENTICATED'))
                order by i.document_id limit 501
                """,(r,n)->new Evidence(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class)),member);
    }
    public record Source(UUID version,String title,String text) {}
    public Source source(UUID content) {
        return jdbc.query("""
                select v.content_version_id,v.title,v.body from learning_contents c
                join learning_content_versions v on v.content_version_id=c.current_published_version_id and v.content_id=c.content_id
                where c.content_id=? and c.status='PUBLISHED' and v.status='PUBLISHED' and v.archived_at is null
                and v.published_at is not null and v.published_at<=now() and c.visibility in ('PUBLIC','AUTHENTICATED')
                """,(r,n)->new Source(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),content)
                .stream().findFirst().orElseThrow(ChatFailure::missing);
    }
}
