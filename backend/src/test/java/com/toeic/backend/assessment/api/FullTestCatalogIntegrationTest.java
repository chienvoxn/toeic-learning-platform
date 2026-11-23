package com.toeic.backend.assessment.api;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FullTestCatalogIntegrationTest extends PracticeTestSupport {

    private static final UUID TEST_ID = UUID.fromString("e5000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("e5100000-0000-0000-0000-000000000001");

    @BeforeEach
    void createPublishedFullTest() {
        jdbc.update("""
                INSERT INTO questions (
                    question_id, part_id, question_type_id, created_by,
                    status, created_at, updated_at
                )
                SELECT ('e4000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
                       (SELECT part_id FROM toeic_parts WHERE part_number = 5),
                       (SELECT question_type_id FROM question_types WHERE code = 'SINGLE_CHOICE'),
                       ?, 'PUBLISHED', now(), now()
                FROM generate_series(1, 200) AS n
                """, TEACHER);
        jdbc.update("""
                INSERT INTO question_versions (
                    question_version_id, question_id, version_number,
                    question_text, status, published_at, created_at
                )
                SELECT ('e4100000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
                       ('e4000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
                       1, 'Catalog question ' || n, 'PUBLISHED', now(), now()
                FROM generate_series(1, 200) AS n
                """);
        jdbc.update("""
                INSERT INTO tests (
                    test_id, created_by, title, description, test_type,
                    status, created_at, updated_at
                ) VALUES (?, ?, 'Published TOEIC Test', 'Catalog description',
                          'FULL_TEST', 'PUBLISHED', now(), now())
                """, TEST_ID, TEACHER);
        jdbc.update("""
                INSERT INTO test_versions (
                    test_version_id, test_id, version_number, duration_seconds,
                    total_questions, status, published_at, created_at
                ) VALUES (?, ?, 1, 7200, 200, 'PUBLISHED', now(), now())
                """, VERSION_ID, TEST_ID);
        jdbc.update("""
                INSERT INTO test_questions (
                    test_version_id, question_version_id,
                    question_order, points
                )
                SELECT ?,
                       ('e4100000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
                       n, 1.00
                FROM generate_series(1, 200) AS n
                """, VERSION_ID);
    }

    @AfterEach
    void removePublishedFullTest() {
        jdbc.update("DELETE FROM test_questions WHERE test_version_id = ?", VERSION_ID);
        jdbc.update("DELETE FROM test_versions WHERE test_id = ?", TEST_ID);
        jdbc.update("DELETE FROM tests WHERE test_id = ?", TEST_ID);
        jdbc.update("DELETE FROM question_versions WHERE question_version_id::text LIKE 'e4100000-0000-0000-0000-%'");
        jdbc.update("DELETE FROM questions WHERE question_id::text LIKE 'e4000000-0000-0000-0000-%'");
    }

    @Test
    void learnerSeesOnlyStartablePublishedFullTests() throws Exception {
        var response = tree(call(get("/api/v1/full-tests"), token(ACTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].testId").value(TEST_ID.toString()))
                .andExpect(jsonPath("$[0].testVersionId").value(VERSION_ID.toString()))
                .andExpect(jsonPath("$[0].title").value("Published TOEIC Test"))
                .andExpect(jsonPath("$[0].durationSeconds").value(7200))
                .andExpect(jsonPath("$[0].totalQuestions").value(200)));
        assertEquals(1, response.size());

        jdbc.update("""
                UPDATE question_versions
                SET status = 'ARCHIVED', archived_at = now()
                WHERE question_version_id =
                    'e4100000-0000-0000-0000-000000000001'::uuid
                """);

        call(get("/api/v1/full-tests"), token(ACTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void catalogRequiresAuthenticationAndLearnerPermission() throws Exception {
        call(get("/api/v1/full-tests"), null)
                .andExpect(status().isUnauthorized());
        call(get("/api/v1/full-tests"), token(TEACHER))
                .andExpect(status().isForbidden());
    }
}
