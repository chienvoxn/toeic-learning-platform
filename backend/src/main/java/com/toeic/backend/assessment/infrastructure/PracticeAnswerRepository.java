package com.toeic.backend.assessment.infrastructure;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Lưu lựa chọn/kết quả trong schema V5; caller giữ khóa attempt đến khi commit. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PracticeAnswerRepository {
    public record Answer(UUID questionVersionId, UUID selectedChoiceId, Boolean correct, OffsetDateTime updatedAt,
            UUID correctChoiceId, String explanation, Integer partNumber) {
        @Override public String toString() { return "PracticeAnswer[redacted]"; }
    }
    private final JdbcTemplate jdbc;
    public PracticeAnswerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean choiceBelongs(UUID versionId, UUID choiceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from question_choices where question_version_id=? and choice_id=?)
                """, Boolean.class, versionId, choiceId));
    }
    public void save(UUID attemptId, UUID versionId, UUID choiceId, OffsetDateTime now) {
        jdbc.update("""
                insert into attempt_answers(attempt_answer_id,attempt_id,question_version_id,selected_choice_id,answered_at,updated_at)
                values (?,?,?,?,?,?)
                on conflict(attempt_id,question_version_id) do update
                set selected_choice_id=excluded.selected_choice_id,answered_at=excluded.answered_at,
                    updated_at=excluded.updated_at,is_correct=null,
                    correct_choice_id=null,explanation_snapshot=null,part_number_snapshot=null
                """, UUID.randomUUID(), attemptId, versionId, choiceId, choiceId == null ? null : now, now);
    }
    public Map<UUID, Answer> find(UUID attemptId) {
        var result = new HashMap<UUID, Answer>();
        jdbc.query("""
                select question_version_id,selected_choice_id,is_correct,updated_at,correct_choice_id,explanation_snapshot,part_number_snapshot
                from attempt_answers where attempt_id=?
                """, rs -> {
            var answer = new Answer(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getObject(3, Boolean.class), rs.getObject(4, OffsetDateTime.class),rs.getObject(5,UUID.class),
                    rs.getString(6),rs.getObject(7,Integer.class));
            result.put(answer.questionVersionId(), answer);
        }, attemptId);
        return Map.copyOf(result);
    }
    public void mark(UUID attemptId, UUID versionId, UUID choiceId, boolean correct, UUID correctChoiceId,
            String explanation, int partNumber, OffsetDateTime now) {
        // Bỏ trống cũng có grading snapshot; không thay answered_at/updated_at của autosave đã xác nhận.
        jdbc.update("""
                insert into attempt_answers(attempt_answer_id,attempt_id,question_version_id,selected_choice_id,is_correct,updated_at,
                    correct_choice_id,explanation_snapshot,part_number_snapshot)
                values (?,?,?,?,?,?,?,?,?) on conflict(attempt_id,question_version_id) do update
                set is_correct=excluded.is_correct,correct_choice_id=excluded.correct_choice_id,
                    explanation_snapshot=excluded.explanation_snapshot,part_number_snapshot=excluded.part_number_snapshot
                """, UUID.randomUUID(), attemptId, versionId, choiceId, correct, now, correctChoiceId, explanation, partNumber);
    }
    public void partResult(UUID attemptId, int partId, int total, int correct, java.math.BigDecimal accuracy) {
        jdbc.update("""
                insert into attempt_part_results(attempt_part_result_id,attempt_id,part_id,total_questions,correct_count,accuracy)
                values (?,?,?,?,?,?)
                """, UUID.randomUUID(), attemptId, partId, total, correct, accuracy);
    }
}
