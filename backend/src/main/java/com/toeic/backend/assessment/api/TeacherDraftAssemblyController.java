package com.toeic.backend.assessment.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/teacher/test-versions")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherDraftAssemblyController {

    private final JdbcTemplate jdbc;

    public TeacherDraftAssemblyController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/{versionId}/assemble-demo")
    @Transactional
    public Map<String, Object> assemble(
            @PathVariable UUID versionId,
            @AuthenticationPrincipal SessionPrincipal actor) {

        UUID userId = actor.userId();

        // Lock the target draft to prevent concurrent assembly.
        List<Map<String, Object>> versions = jdbc.queryForList("""
            SELECT tv.status AS version_status, t.status AS test_status
            FROM test_versions tv
            JOIN tests t ON t.test_id = tv.test_id
            WHERE tv.test_version_id = ?
              AND t.created_by = ?
            FOR UPDATE OF tv
            """, versionId, userId);

        if (versions.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Test version not found");
        }

        Map<String, Object> version = versions.get(0);
        if (!"DRAFT".equals(version.get("version_status"))
                || !"DRAFT".equals(version.get("test_status"))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only a draft test can be assembled");
        }

        Integer existing = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM test_questions
            WHERE test_version_id = ?
            """, Integer.class, versionId);

        if (existing == null || existing != 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Test version already has questions");
        }

        // This demo endpoint deliberately requires exactly 200
        // draft question versions owned by the current teacher.
        Integer available = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM question_versions qv
            JOIN questions q ON q.question_id = qv.question_id
            WHERE q.created_by = ?
              AND q.status = 'DRAFT'
              AND qv.status = 'DRAFT'
            """, Integer.class, userId);

        if (available == null || available != 200) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Expected exactly 200 owned draft question versions");
        }

        // Every question must have one correct choice and 3-4 choices.
        Integer validChoices = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM (
                SELECT qv.question_version_id
                FROM question_versions qv
                JOIN questions q ON q.question_id = qv.question_id
                JOIN question_choices c
                  ON c.question_version_id = qv.question_version_id
                WHERE q.created_by = ?
                  AND q.status = 'DRAFT'
                  AND qv.status = 'DRAFT'
                GROUP BY qv.question_version_id
                HAVING COUNT(*) BETWEEN 3 AND 4
                   AND COUNT(*) FILTER (WHERE c.is_correct) = 1
            ) valid
            """, Integer.class, userId);

        if (validChoices == null || validChoices != 200) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Invalid answer choices");
        }

        Integer invalidGroups = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM question_versions qv
            JOIN questions q ON q.question_id = qv.question_id
            WHERE q.created_by = ?
              AND q.status = 'DRAFT'
              AND qv.status = 'DRAFT'
              AND (
                (q.group_id IS NULL AND qv.group_version_id IS NOT NULL)
                OR
                (q.group_id IS NOT NULL AND qv.group_version_id IS NULL)
              )
            """, Integer.class, userId);

        if (invalidGroups == null || invalidGroups != 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Invalid question-group linkage");
        }

        // Keep questions from the same group together.
        int inserted = jdbc.update("""
            WITH ordered AS (
                SELECT
                    qv.question_version_id,
                    qv.group_version_id,
                    ROW_NUMBER() OVER (
                        ORDER BY p.part_number,
                            COALESCE(
                                qv.group_version_id::text,
                                qv.question_version_id::text
                            ),
                            qv.question_version_id
                    ) AS position
                FROM question_versions qv
                JOIN questions q ON q.question_id = qv.question_id
                JOIN toeic_parts p ON p.part_id = q.part_id
                WHERE q.created_by = ?
                  AND q.status = 'DRAFT'
                  AND qv.status = 'DRAFT'
            )
            INSERT INTO test_questions (
                test_version_id,
                question_version_id,
                question_group_version_id,
                question_order,
                points
            )
            SELECT ?, question_version_id, group_version_id,
                   position::integer, 1.00
            FROM ordered
            """, userId, versionId);

        if (inserted != 200) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Incomplete test assembly");
        }

        jdbc.update("""
            UPDATE test_versions
            SET total_questions = ?
            WHERE test_version_id = ?
            """, inserted, versionId);

        return Map.of(
            "testVersionId", versionId,
            "status", "DRAFT",
            "attachedQuestions", inserted
        );
    }
}
