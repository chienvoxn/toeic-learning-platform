package com.toeic.backend.listening.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Truy vấn và lưu trữ metadata media_resources cũng như liên kết media/transcript của các câu hỏi đã pin trong attempt. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ListeningMediaRepository {
    public record MediaRow(
            UUID resourceId,
            String resourceType,
            String storageUrl,
            String mimeType,
            Long fileSize,
            Integer durationSeconds,
            String visibility,
            UUID ownerId
    ) {
    }

    public record LinkedAssetRow(
            UUID resourceId,
            String resourceType,
            String storageUrl,
            String mimeType,
            Long fileSize,
            Integer durationSeconds,
            Integer displayOrder,
            String transcript
    ) {
    }

    public record AttemptHeader(
            UUID attemptId,
            UUID userId,
            String sourceType,
            String status
    ) {
    }

    public record AttemptQuestionPin(
            int questionOrder,
            UUID questionVersionId,
            UUID groupVersionId,
            int partNumber
    ) {
    }

    private final JdbcTemplate jdbc;

    public ListeningMediaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertMedia(
            UUID resourceId,
            String resourceType,
            String storageUrl,
            String mimeType,
            long fileSize,
            Integer durationSeconds,
            String visibility,
            UUID ownerId,
            OffsetDateTime createdAt
    ) {
        jdbc.update("""
                INSERT INTO media_resources(
                    resource_id, resource_type, storage_url, mime_type, file_size,
                    duration_seconds, transcript, visibility, owner_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)
                """,
                resourceId, resourceType, storageUrl, mimeType, fileSize,
                durationSeconds, visibility, ownerId, createdAt
        );
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<MediaRow> findAuthorAccessibleMedia(UUID resourceId, UUID actorId, boolean canManageAny) {
        var rows = jdbc.query("""
                SELECT m.resource_id, m.resource_type, m.storage_url, m.mime_type, m.file_size,
                       m.duration_seconds, m.visibility, m.owner_id
                FROM media_resources m
                WHERE m.resource_id = ?
                  AND (
                      m.owner_id = ?
                      OR ? = TRUE
                      OR EXISTS (
                          SELECT 1 FROM media_reuse_grants g
                          WHERE g.media_id = m.resource_id
                            AND g.user_id = ?
                            AND g.revoked_at IS NULL
                      )
                  )
                """,
                (rs, i) -> new MediaRow(
                        rs.getObject("resource_id", UUID.class),
                        rs.getString("resource_type"),
                        rs.getString("storage_url"),
                        rs.getString("mime_type"),
                        rs.getObject("file_size") == null ? null : rs.getLong("file_size"),
                        rs.getObject("duration_seconds") == null ? null : rs.getInt("duration_seconds"),
                        rs.getString("visibility"),
                        rs.getObject("owner_id", UUID.class)
                ),
                resourceId, actorId, canManageAny, actorId
        );
        return rows.stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<AttemptHeader> findOwnAttempt(UUID actorId, UUID attemptId) {
        var rows = jdbc.query("""
                SELECT attempt_id, user_id, attempt_source_type, status
                FROM attempts
                WHERE attempt_id = ?
                  AND user_id = ?
                  AND (attempt_source_type IN ('PRACTICE_SNAPSHOT', 'SECTION_SNAPSHOT') OR (attempt_source_type='TEST_VERSION' AND test_blueprint_snapshot IS NOT NULL))
                """,
                (rs, i) -> new AttemptHeader(
                        rs.getObject("attempt_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("attempt_source_type"),
                        rs.getString("status")
                ),
                attemptId, actorId
        );
        return rows.stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<AttemptQuestionPin> findAttemptPins(UUID attemptId) {
        return jdbc.query("""
                SELECT s.question_order, s.question_version_id, s.question_group_version_id, p.part_number
                FROM attempt_question_snapshots s
                JOIN question_versions qv ON qv.question_version_id = s.question_version_id
                JOIN questions q ON q.question_id = qv.question_id
                JOIN toeic_parts p ON p.part_id = q.part_id
                WHERE s.attempt_id = ?
                ORDER BY s.question_order
                """,
                (rs, i) -> new AttemptQuestionPin(
                        rs.getInt("question_order"),
                        rs.getObject("question_version_id", UUID.class),
                        rs.getObject("question_group_version_id", UUID.class),
                        rs.getInt("part_number")
                ),
                attemptId
        );
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<LinkedAssetRow> findReadableQuestionAssets(UUID questionVersionId) {
        return jdbc.query("""
                SELECT m.resource_id, m.resource_type, m.storage_url, m.mime_type,
                       m.file_size, m.duration_seconds, r.transcript
                FROM question_resources r
                JOIN media_resources m ON m.resource_id = r.resource_id
                WHERE r.question_version_id = ?
                  AND m.visibility IN ('PUBLIC', 'AUTHENTICATED')
                ORDER BY m.resource_id
                """,
                (rs, i) -> new LinkedAssetRow(
                        rs.getObject("resource_id", UUID.class),
                        rs.getString("resource_type"),
                        rs.getString("storage_url"),
                        rs.getString("mime_type"),
                        rs.getObject("file_size") == null ? null : rs.getLong("file_size"),
                        rs.getObject("duration_seconds") == null ? null : rs.getInt("duration_seconds"),
                        null,
                        rs.getString("transcript")
                ),
                questionVersionId
        );
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<LinkedAssetRow> findReadableGroupAssets(UUID groupVersionId) {
        return jdbc.query("""
                SELECT m.resource_id, m.resource_type, m.storage_url, m.mime_type,
                       m.file_size, m.duration_seconds, r.display_order, r.transcript
                FROM question_group_version_resources r
                JOIN media_resources m ON m.resource_id = r.resource_id
                WHERE r.group_version_id = ?
                  AND m.visibility IN ('PUBLIC', 'AUTHENTICATED')
                ORDER BY r.display_order, m.resource_id
                """,
                (rs, i) -> new LinkedAssetRow(
                        rs.getObject("resource_id", UUID.class),
                        rs.getString("resource_type"),
                        rs.getString("storage_url"),
                        rs.getString("mime_type"),
                        rs.getObject("file_size") == null ? null : rs.getLong("file_size"),
                        rs.getObject("duration_seconds") == null ? null : rs.getInt("duration_seconds"),
                        rs.getObject("display_order") == null ? null : rs.getInt("display_order"),
                        rs.getString("transcript")
                ),
                groupVersionId
        );
    }
}
