package com.toeic.backend.ai.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExamGuardService {

    private final JdbcTemplate jdbc;

    public ExamGuardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasActiveExam(UUID userId) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM attempts
                    WHERE user_id = ?
                      AND attempt_type IN ('SECTION', 'FULL_TEST')
                      AND status = 'IN_PROGRESS'
                      AND expires_at > clock_timestamp()
                )
                """, Boolean.class, userId)
        );
    }

    public void requireAiAllowed(UUID userId) {
        if (hasActiveExam(userId)) {
            throw new ChatFailure(
                403,
                "AI_EXAM_RESTRICTED",
                "AI Tutor is unavailable during an active exam."
            );
        }
    }
}
