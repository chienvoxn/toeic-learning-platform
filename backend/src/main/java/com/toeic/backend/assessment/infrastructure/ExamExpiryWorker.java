package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.application.ExamAttemptService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.exams.expiry-enabled",havingValue="true",matchIfMissing=true)
public class ExamExpiryWorker {
    private final ExamAttemptService attempts;
    private final ExamRepository db;
    private final Clock clock;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ExamExpiryWorker.class);
    public ExamExpiryWorker(ExamAttemptService attempts,ExamRepository db,Clock clock){this.attempts=attempts;this.db=db;this.clock=clock;}
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void expire(){
        for(var row:db.rows("select attempt_id from attempts where status='IN_PROGRESS' and expires_at<=? order by expires_at limit 100",OffsetDateTime.now(clock))) {
            var id=(UUID)row.get("attempt_id");
            try { attempts.expire(id); } catch(RuntimeException e) { log.warn("Exam expiry could not grade attempt {} ({})",id,e.getClass().getSimpleName()); }
        }
    }
}
