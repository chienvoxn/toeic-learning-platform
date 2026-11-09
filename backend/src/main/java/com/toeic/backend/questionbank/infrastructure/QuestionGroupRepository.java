package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.domain.QuestionGroup;
import com.toeic.backend.questionbank.domain.QuestionGroupVersion;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class QuestionGroupRepository {
    private final EntityManager entityManager;
    public QuestionGroupRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public void persistNew(QuestionGroup group) { entityManager.persist(group); }
    public void persistNew(QuestionGroupVersion version) { entityManager.persist(version); }
    public Optional<QuestionGroup> findById(UUID groupId) {
        return Optional.ofNullable(entityManager.find(QuestionGroup.class, groupId));
    }
    public Optional<QuestionGroupVersion> findVersionById(UUID versionId) {
        return entityManager.createQuery("""
                select v from QuestionGroupVersion v join fetch v.group where v.groupVersionId = :id
                """, QuestionGroupVersion.class).setParameter("id", versionId).getResultList().stream().findFirst();
    }

    /** Exact group history, without selecting latest/fallback or inferring ownership. */
    public List<QuestionGroupVersion> findAllVersions(UUID groupId) {
        return entityManager.createQuery("""
                select v from QuestionGroupVersion v join fetch v.group
                where v.group.groupId = :id order by v.versionNumber
                """, QuestionGroupVersion.class).setParameter("id", groupId).getResultList();
    }
}
