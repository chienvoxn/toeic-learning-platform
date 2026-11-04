package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.domain.QuestionAssetMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Đọc metadata từ bảng media dùng chung qua link của đúng version, không tạo entity media thứ hai. */
@Repository
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class QuestionAssetRepository {
    private static final String FIELDS = """
            select m.resource_id, m.resource_type, m.mime_type, m.file_size, m.duration_seconds
            from media_resources m
            """;
    private final EntityManager entityManager;
    public QuestionAssetRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public List<QuestionAssetMetadata> findForQuestionVersion(UUID versionId) {
        return read(FIELDS + """
                join question_resources r on r.resource_id = m.resource_id
                where r.question_version_id = :id order by m.resource_id
                """, versionId);
    }
    public List<QuestionAssetMetadata> findForGroupVersion(UUID versionId) {
        return read(FIELDS + """
                join question_group_version_resources r on r.resource_id = m.resource_id
                where r.group_version_id = :id order by r.display_order, m.resource_id
                """, versionId);
    }
    public List<QuestionAssetMetadata> findReadableForQuestionVersion(UUID versionId) {
        return read(FIELDS + """
                join question_resources r on r.resource_id = m.resource_id
                where r.question_version_id = :id and m.visibility in ('PUBLIC', 'AUTHENTICATED')
                order by m.resource_id
                """, versionId);
    }
    public List<QuestionAssetMetadata> findReadableForGroupVersion(UUID versionId) {
        return read(FIELDS + """
                join question_group_version_resources r on r.resource_id = m.resource_id
                where r.group_version_id = :id and m.visibility in ('PUBLIC', 'AUTHENTICATED')
                order by r.display_order, m.resource_id
                """, versionId);
    }
    private List<QuestionAssetMetadata> read(String sql, UUID versionId) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = entityManager.createNativeQuery(sql, Tuple.class).setParameter("id", versionId).getResultList();
        return rows.stream().map(row -> new QuestionAssetMetadata(row.get("resource_id", UUID.class),
                row.get("resource_type", String.class), row.get("mime_type", String.class),
                row.get("file_size") == null ? null : ((Number) row.get("file_size")).longValue(),
                row.get("duration_seconds") == null ? null : ((Number) row.get("duration_seconds")).intValue())).toList();
    }
}
