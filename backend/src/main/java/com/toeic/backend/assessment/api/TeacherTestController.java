
package com.toeic.backend.assessment.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/teacher/tests")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherTestController {

    private final JdbcTemplate jdbc;

    public TeacherTestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CreateTestRequest(
            String title,
            String description
    ) {}

    public record TestSummary(
            UUID testId,
            String title,
            String description,
            String testType,
            String status
    ) {}

    public record CreatedTest(
            UUID testId,
            UUID testVersionId,
            String title,
            String status
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<TestSummary> list(
            @AuthenticationPrincipal SessionPrincipal actor
    ) {
        return jdbc.query("""
                SELECT test_id, title, description,
                       test_type, status
                FROM tests
                WHERE created_by = ?
                ORDER BY updated_at DESC, test_id ASC
                """,
                (rs, rowNum) -> new TestSummary(
                        rs.getObject("test_id", UUID.class),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("test_type"),
                        rs.getString("status")
                ),
                actor.userId()
        );
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CreatedTest> create(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestBody CreateTestRequest request
    ) {
        if (request == null
                || request.title() == null
                || request.title().isBlank()
                || request.title().length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tieu de de thi khong hop le"
            );
        }

        String title = request.title().trim();
        UUID testId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO tests (
                    test_id, created_by, title,
                    description, test_type, status,
                    created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'FULL_TEST', 'DRAFT', now(), now())
                """,
                testId,
                actor.userId(),
                title,
                request.description()
        );

        jdbc.update("""
                INSERT INTO test_versions (
                    test_version_id, test_id,
                    version_number, duration_seconds,
                    total_questions, status, created_at
                )
                VALUES (?, ?, 1, 7200, 0, 'DRAFT', now())
                """,
                versionId,
                testId
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedTest(
                        testId, versionId, title, "DRAFT"
                ));
    }
}
