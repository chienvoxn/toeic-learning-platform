package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.application.ExamException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Persistence cho đề/attempt; dùng cùng transaction manager với ngân hàng câu hỏi. */
@Repository
public class ExamRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ExamRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public int write(String sql,Object...args) { return jdbc.update(sql,args); }
    public List<Map<String,Object>> rows(String sql,Object...args) { return jdbc.queryForList(sql,args); }
    public Map<String,Object> one(String sql,Object...args) { var rows=rows(sql,args); if(rows.isEmpty())throw ExamException.missing(); return rows.getFirst(); }
    public String json(Object value) { return json.writeValueAsString(value); }
    public <T> T parse(Object value,Class<T> type) { return json.readValue(value.toString(),type); }
    public void audit(UUID actor,String action,UUID id) { write("insert into audit_logs(audit_id,actor_user_id,action,entity_type,entity_id) values(?,?,?,'EXAM',?)",UUID.randomUUID(),actor,action,id); }
    public void event(String type,UUID id) { write("insert into outbox_events(event_id,event_type,aggregate_type,aggregate_id,payload_json,status) values(?,?,'ATTEMPT',?,'{}','PENDING')",UUID.randomUUID(),type,id); }
}
