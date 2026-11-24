package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.application.AttemptHistoryDtos;
import com.toeic.backend.questionbank.application.PracticeQuestionCatalog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AttemptHistoryRepository {
    private static final String OWN = """
            user_id=:actor and (
              (:practice and attempt_source_type='PRACTICE_SNAPSHOT') or
              (:exam and (attempt_source_type='SECTION_SNAPSHOT' or
                (attempt_source_type='TEST_VERSION' and (test_blueprint_snapshot is not null or (attempt_type='FULL_TEST' AND test_version_id IS NOT NULL AND test_blueprint_snapshot IS NULL
                    AND total_questions=200 AND expires_at=started_at+interval '120 minutes'
                    AND (SELECT count(*) FROM attempt_question_snapshots s WHERE s.attempt_id=attempts.attempt_id)=200
                    AND (SELECT count(DISTINCT s.question_version_id) FROM attempt_question_snapshots s WHERE s.attempt_id=attempts.attempt_id)=200
                    AND (SELECT min(s.question_order) FROM attempt_question_snapshots s WHERE s.attempt_id=attempts.attempt_id)=1
                    AND (SELECT max(s.question_order) FROM attempt_question_snapshots s WHERE s.attempt_id=attempts.attempt_id)=200))))))
            """;
    private final NamedParameterJdbcTemplate jdbc;
    public AttemptHistoryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AttemptHistoryDtos.Page find(UUID actor, boolean practice, boolean exam, int page, int size) {
        var args = new MapSqlParameterSource().addValue("actor", actor).addValue("practice", practice)
                .addValue("exam", exam).addValue("limit", size).addValue("offset", (long) page * size);
        long total = jdbc.queryForObject("select count(*) from attempts where " + OWN, args, Long.class);
        // Page first; aggregate only persisted grading rows belonging to these attempts, never the bank.
        var items = jdbc.query("""
                with selected as (select * from attempts where %s
                  order by started_at desc, attempt_id desc limit :limit offset :offset)
                select a.*, r.graded, r.correct, r.unanswered from selected a
                left join lateral (
                  select count(*) filter(where is_correct is not null) graded,
                    count(*) filter(where is_correct=true) correct,
                    count(*) filter(where is_correct is not null and selected_choice_id is null) unanswered
                  from attempt_answers where attempt_id=a.attempt_id and a.status='GRADED'
                ) r on true order by a.started_at desc, a.attempt_id desc
                """.formatted(OWN), args, (rs, row) -> {
            int count = rs.getInt("total_questions");
            AttemptHistoryDtos.Score score = null;
            if ("GRADED".equals(rs.getString("status"))) {
                int correct = rs.getInt("correct_count"), unanswered = rs.getInt("unanswered");
                if (rs.getObject("correct_count") == null || count < 1 || rs.getInt("graded") != count
                        || rs.getInt("correct") != correct || correct + unanswered > count) {
                    throw new PracticeQuestionCatalog.Unavailable(false);
                }
                var accuracy = BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                score = new AttemptHistoryDtos.Score(correct, count - correct - unanswered, unanswered, correct, accuracy);
            }
            return new AttemptHistoryDtos.Item(rs.getObject("attempt_id", UUID.class), rs.getString("attempt_source_type"),
                    rs.getString("attempt_type"), rs.getString("status"), rs.getObject("started_at", OffsetDateTime.class),
                    rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("submitted_at", OffsetDateTime.class), count, score);
        });
        return new AttemptHistoryDtos.Page(List.copyOf(items), page, size, total, (total + size - 1) / size);
    }
}
