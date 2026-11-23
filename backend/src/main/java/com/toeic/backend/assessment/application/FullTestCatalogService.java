package com.toeic.backend.assessment.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FullTestCatalogService {

    private static final int FULL_TEST_QUESTION_COUNT = 200;

    private final JdbcTemplate jdbc;

    public FullTestCatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PublishedFullTest(
            UUID testId,
            UUID testVersionId,
            String title,
            String description,
            int durationSeconds,
            int totalQuestions,
            OffsetDateTime publishedAt
    ) {}

    /**
     * Trả về các Full Test đã xuất bản và thực sự có thể bắt đầu.
     * Catalog kiểm tra cùng điều kiện published/200 câu với use case tạo attempt
     * để learner không nhìn thấy một đề mà máy chủ chắc chắn sẽ từ chối.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('exam:manage:self')")
    public List<PublishedFullTest> listPublished() {
        return jdbc.query("""
                SELECT t.test_id,
                       tv.test_version_id,
                       t.title,
                       t.description,
                       tv.duration_seconds,
                       tv.total_questions,
                       tv.published_at
                FROM test_versions tv
                JOIN tests t ON t.test_id = tv.test_id
                WHERE t.test_type = 'FULL_TEST'
                  AND t.status = 'PUBLISHED'
                  AND tv.status = 'PUBLISHED'
                  AND tv.duration_seconds IS NOT NULL
                  AND tv.total_questions = ?
                  AND (
                      SELECT COUNT(*)
                      FROM test_questions tq
                      JOIN question_versions qv
                        ON qv.question_version_id = tq.question_version_id
                      LEFT JOIN question_group_versions qgv
                        ON qgv.group_version_id = tq.question_group_version_id
                      WHERE tq.test_version_id = tv.test_version_id
                        AND qv.status = 'PUBLISHED'
                        AND (
                            tq.question_group_version_id IS NULL
                            OR qgv.status = 'PUBLISHED'
                        )
                  ) = ?
                ORDER BY tv.published_at DESC NULLS LAST,
                         t.title ASC,
                         tv.version_number DESC
                """,
                (rs, rowNum) -> new PublishedFullTest(
                        rs.getObject("test_id", UUID.class),
                        rs.getObject("test_version_id", UUID.class),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("duration_seconds"),
                        rs.getInt("total_questions"),
                        rs.getObject("published_at", OffsetDateTime.class)
                ),
                FULL_TEST_QUESTION_COUNT,
                FULL_TEST_QUESTION_COUNT
        );
    }
}
