package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    public void lockExamActor(UUID actorId) {
        entityManager.createNativeQuery(
                "SELECT user_id FROM users WHERE user_id = :actor FOR UPDATE")
                .setParameter("actor", actorId)
                .getResultList();
    }

    public void persist(PracticeAttempt attempt) { entityManager.persist(attempt); entityManager.flush(); }
    /** Khóa parent trước khi đọc status/expiry và trước mọi ghi đáp án hoặc submit. */
    public Optional<PracticeAttempt> lockOwn(UUID actorId, UUID attemptId) {
        // Không fetch join collection: PostgreSQL không khóa phía nullable của outer join.
        return entityManager.createQuery("""
                select a from PracticeAttempt a
                where a.attemptId = :id and a.userId = :actor and (
                    a.sourceType in ('PRACTICE_SNAPSHOT','SECTION_SNAPSHOT')
                    or (
                        a.sourceType = 'TEST_VERSION'
                        and a.attemptType = 'FULL_TEST'
                        and a.testVersionId is not null
                    )
                )
                """, PracticeAttempt.class).setParameter("id", attemptId).setParameter("actor", actorId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList().stream().findFirst();
    }
    public Optional<PracticeAttempt> findOwn(UUID actorId, UUID attemptId) {
        return entityManager.createQuery("""
                select a from PracticeAttempt a left join fetch a.questions
                where a.attemptId = :id and a.userId = :actor and (
                    a.sourceType in ('PRACTICE_SNAPSHOT','SECTION_SNAPSHOT')
                    or (
                        a.sourceType = 'TEST_VERSION'
                        and a.attemptType = 'FULL_TEST'
                        and a.testVersionId is not null
                    )
                )
                """, PracticeAttempt.class).setParameter("id", attemptId).setParameter("actor", actorId)
                .getResultList().stream().findFirst();
    }
}
