package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.domain.Question;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lưu identity mới và đọc metadata; caller quản lý transaction của use case. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class QuestionRepository {
    private final EntityManager entityManager;
    public QuestionRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public void persistNew(Question question) { entityManager.persist(question); }
    public Optional<Question> findById(UUID questionId) {
        return entityManager.createQuery("""
                select q from Question q join fetch q.part join fetch q.type
                left join fetch q.topic left join fetch q.group where q.questionId = :id
                """, Question.class).setParameter("id", questionId).getResultList().stream().findFirst();
    }
}
