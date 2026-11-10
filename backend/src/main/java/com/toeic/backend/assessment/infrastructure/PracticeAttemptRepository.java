package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PracticeAttemptRepository {
    private final EntityManager entityManager;
    public PracticeAttemptRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public void persist(PracticeAttempt attempt) { entityManager.persist(attempt); entityManager.flush(); }
    public Optional<PracticeAttempt> findOwn(UUID actorId, UUID attemptId) {
        return entityManager.createQuery("""
                select a from PracticeAttempt a left join fetch a.questions
                where a.attemptId = :id and a.userId = :actor and a.sourceType = 'PRACTICE_SNAPSHOT'
                """, PracticeAttempt.class).setParameter("id", attemptId).setParameter("actor", actorId)
                .getResultList().stream().findFirst();
    }
}
