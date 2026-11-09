package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.domain.Question;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    /** Creator-scoped primitive; the application service must authorize its trusted actor first. */
    public Optional<Question> findByIdAndCreator(UUID questionId, UUID creatorId) {
        Objects.requireNonNull(creatorId, "creatorId");
        return entityManager.createQuery("""
                select q from Question q join fetch q.part join fetch q.type
                left join fetch q.topic left join fetch q.group
                where q.questionId = :id and q.createdBy = :creator
                """, Question.class).setParameter("id", questionId).setParameter("creator", creatorId)
                .getResultList().stream().findFirst();
    }

    /** Data and count share the creator predicate; null never means unrestricted access. */
    public Page<Question> findByCreator(UUID creatorId, Pageable pageable) {
        Objects.requireNonNull(creatorId, "creatorId");
        if (pageable == null || pageable.isUnpaged() || pageable.getPageSize() > 100) {
            throw new IllegalArgumentException("Invalid authoring page");
        }
        var fields = Map.of("questionId", "q.questionId", "createdAt", "q.createdAt", "updatedAt", "q.updatedAt");
        var ordering = pageable.getSort().stream().map(order -> {
            var field = fields.get(order.getProperty());
            if (field == null) { throw new IllegalArgumentException("Unsupported authoring sort"); }
            return field + " " + order.getDirection().name();
        }).collect(Collectors.joining(", "));
        if (ordering.isEmpty()) { ordering = "q.updatedAt desc"; }
        if (pageable.getSort().getOrderFor("questionId") == null) { ordering += ", q.questionId asc"; }
        var predicate = " where q.createdBy = :creator";
        var count = entityManager.createQuery("select count(q) from Question q" + predicate, Long.class)
                .setParameter("creator", creatorId).getSingleResult();
        var rows = entityManager.createQuery("""
                select q from Question q join fetch q.part join fetch q.type
                left join fetch q.topic left join fetch q.group
                """ + predicate + " order by " + ordering, Question.class)
                .setParameter("creator", creatorId).setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize()).getResultList();
        return new PageImpl<>(rows, pageable, count);
    }
}
