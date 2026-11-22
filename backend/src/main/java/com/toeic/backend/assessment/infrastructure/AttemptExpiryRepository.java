package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Claim bằng khóa parent SKIP LOCKED; retry lưu bền, không dịch deadline đã pin. */
@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class AttemptExpiryRepository {
    private final NamedParameterJdbcTemplate jdbc;private final EntityManager entities;
    public AttemptExpiryRepository(NamedParameterJdbcTemplate jdbc,EntityManager entities){this.jdbc=jdbc;this.entities=entities;}
    public Optional<UUID> next(OffsetDateTime now,Set<UUID> attempted){
        var params=new HashMap<String,Object>();params.put("now",now);params.put("attempted",attempted);
        var ids=jdbc.query("""
            SELECT a.attempt_id FROM attempts a LEFT JOIN attempt_expiry_retries r USING(attempt_id)
            WHERE a.attempt_source_type='TEST_VERSION' AND a.attempt_type='FULL_MOCK'
              AND a.test_version_id IS NOT NULL AND a.test_blueprint_snapshot IS NOT NULL
              AND a.status='IN_PROGRESS' AND a.expires_at<=:now
              AND (r.next_retry_at IS NULL OR r.next_retry_at<=:now)
            """+(attempted.isEmpty()?"":" AND a.attempt_id NOT IN (:attempted)")+" ORDER BY a.expires_at,a.attempt_id LIMIT 1 FOR UPDATE OF a SKIP LOCKED",params,(r,n)->r.getObject(1,UUID.class));
        return ids.stream().findFirst();
    }
    public PracticeAttempt load(UUID id){return entities.find(PracticeAttempt.class,id);}
    public void failed(UUID id,OffsetDateTime now){
        var rows=jdbc.query("SELECT attempt_id FROM attempts WHERE attempt_id=:id AND attempt_source_type='TEST_VERSION' AND status='IN_PROGRESS' FOR UPDATE SKIP LOCKED",Map.of("id",id),(r,n)->r.getObject(1,UUID.class));
        if(rows.isEmpty())return;
        jdbc.update("""
            INSERT INTO attempt_expiry_retries(attempt_id,failure_count,next_retry_at,last_failed_at)
            VALUES(:id,1,:now+interval '5 seconds',:now)
            ON CONFLICT(attempt_id) DO UPDATE SET failure_count=least(attempt_expiry_retries.failure_count+1,31),
              next_retry_at=:now+(least(300,5*power(2,least(attempt_expiry_retries.failure_count,6))))*interval '1 second',
              last_failed_at=:now
            """,Map.of("id",id,"now",now));
    }
}
