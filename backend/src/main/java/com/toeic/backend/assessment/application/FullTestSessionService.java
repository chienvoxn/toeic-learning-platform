package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import com.toeic.backend.assessment.infrastructure.PracticeAttemptRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FullTestSessionService {

    private final JdbcTemplate jdbc;
    private final PracticeAttemptRepository attempts;
    private final PracticeSessionService sessions;
    private final Clock clock;

    public FullTestSessionService(
            JdbcTemplate jdbc,
            PracticeAttemptRepository attempts,
            PracticeSessionService sessions,
            Clock clock) {
        this.jdbc = jdbc;
        this.attempts = attempts;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize(
        "hasAuthority('exam:manage:self') " +
        "and @practiceAccess.isActor(#actorId)"
    )
    public PracticeSessionDtos.Session start(
            UUID actorId,
            UUID testVersionId) {

        Long valid = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM test_versions
            WHERE test_version_id = ?
              AND status = 'PUBLISHED'
              AND total_questions = 200
            """, Long.class, testVersionId);

        if (valid == null || valid != 1L) {
            throw PracticeException.invalid();
        }

        List<PracticeAttempt.Pin> pins = jdbc.query("""
            SELECT
                tq.question_version_id,
                tq.question_group_version_id
            FROM test_questions tq
            JOIN question_versions qv
              ON qv.question_version_id =
                 tq.question_version_id
            LEFT JOIN question_group_versions qgv
              ON qgv.group_version_id =
                 tq.question_group_version_id
            WHERE tq.test_version_id = ?
              AND qv.status = 'PUBLISHED'
              AND (
                  tq.question_group_version_id IS NULL
                  OR qgv.status = 'PUBLISHED'
              )
            ORDER BY tq.question_order
            """,
            (rs, rowNum) -> new PracticeAttempt.Pin(
                rs.getObject("question_version_id", UUID.class),
                rs.getObject("question_group_version_id", UUID.class)
            ),
            testVersionId
        );

        if (pins.size() != 200) {
            throw PracticeException.invalid();
        }

        attempts.lockExamActor(actorId);

        OffsetDateTime started = OffsetDateTime.now(clock)
                .truncatedTo(ChronoUnit.MICROS);

        PracticeAttempt attempt = PracticeAttempt.fullTest(
                actorId,
                testVersionId,
                started,
                pins
        );

        attempts.persist(attempt);

        return sessions.view(actorId, attempt);
    }
}
