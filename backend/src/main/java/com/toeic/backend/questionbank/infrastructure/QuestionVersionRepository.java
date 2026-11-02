package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.domain.QuestionVersion;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lưu aggregate mới bằng persist (UUID đã cấp), không merge/xóa lịch sử version. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class QuestionVersionRepository {
    private static final String WITH_CONTEXT = """
            select v from QuestionVersion v join fetch v.question q
            join fetch q.part join fetch q.type left join fetch q.topic left join fetch q.group
            left join fetch v.groupVersion gv left join fetch gv.group
            """;
    private final EntityManager entityManager;
    public QuestionVersionRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public void persistNew(QuestionVersion version) {
        version.verifyReferences();
        entityManager.persist(version);
    }
    public Optional<QuestionVersion> findById(UUID versionId) {
        return entityManager.createQuery(WITH_CONTEXT + " where v.questionVersionId = :id", QuestionVersion.class)
                .setParameter("id", versionId).getResultList().stream().findFirst();
    }
    public Optional<QuestionVersion> findByQuestionAndNumber(UUID questionId, int versionNumber) {
        return entityManager.createQuery(WITH_CONTEXT
                        + " where q.questionId = :id and v.versionNumber = :number", QuestionVersion.class)
                .setParameter("id", questionId).setParameter("number", versionNumber)
                .getResultList().stream().findFirst();
    }
}
