package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.domain.QuestionVersion;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lưu aggregate mới bằng persist (UUID đã cấp), không merge/xóa lịch sử version. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class QuestionVersionRepository {
    private static final String WITH_CONTEXT = """
            select v from QuestionVersion v join fetch v.question q
            join fetch q.part join fetch q.type left join fetch q.topic left join fetch q.group g
            left join fetch v.groupVersion gv left join fetch gv.group
            """;
    private static final String READ_FROM = """
            from QuestionVersion v join v.question q left join q.group g left join v.groupVersion gv
            """;
    private static final String READABLE = """
             where :readAllowed = true
               and q.currentPublishedVersionId = v.questionVersionId
               and q.status = 'PUBLISHED' and v.status = 'PUBLISHED'
               and v.publishedAt is not null and v.publishedAt <= :now and v.archivedAt is null
               and ((q.group is null and v.groupVersion is null)
                    or (g is not null and gv is not null and gv.group.groupId = g.groupId
                        and g.status = 'PUBLISHED' and gv.status = 'PUBLISHED'
                        and g.currentPublishedVersionId = gv.groupVersionId
                        and gv.publishedAt is not null and gv.publishedAt <= :now and gv.archivedAt is null))
            """;
    private static final String FILTERS = """
               and (:partId is null or q.part.partId = :partId)
               and (:topicId is null or q.topic.topicId = :topicId)
               and (:difficulty is null or v.difficulty = :difficulty)
            """;
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "question.questionId", "q.questionId", "question.part.partId", "q.part.partId",
            "question.topic.topicId", "q.topic.topicId", "difficulty", "v.difficulty", "publishedAt", "v.publishedAt");
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

    /** Ordered history for an already-authorized question; never guesses a published or working version. */
    public List<QuestionVersion> findAllForQuestion(UUID questionId) {
        return entityManager.createQuery(WITH_CONTEXT + " where q.questionId = :id order by v.versionNumber",
                QuestionVersion.class).setParameter("id", questionId).getResultList();
    }

    public Page<QuestionVersion> findReadable(boolean readAllowed, OffsetDateTime now,
                                               Integer partId, Integer topicId, String difficulty, Pageable pageable) {
        var ordering = pageable.getSort().stream().map(order -> {
            var field = SORT_FIELDS.get(order.getProperty());
            if (field == null) { throw new IllegalArgumentException("Unsupported question sort"); }
            return field + " " + order.getDirection().name();
        }).collect(Collectors.joining(", "));
        if (ordering.isEmpty()) { ordering = "v.publishedAt desc, q.questionId asc"; }
        var query = bind(entityManager.createQuery(WITH_CONTEXT + READABLE + FILTERS + " order by " + ordering,
                QuestionVersion.class), readAllowed, now, partId, topicId, difficulty);
        var count = bind(entityManager.createQuery("select count(v) " + READ_FROM + READABLE + FILTERS,
                Long.class), readAllowed, now, partId, topicId, difficulty).getSingleResult();
        var rows = query.setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize()).getResultList();
        return new PageImpl<>(rows, pageable, count);
    }

    public Optional<QuestionVersion> findReadableByQuestionId(UUID questionId, boolean readAllowed, OffsetDateTime now) {
        return entityManager.createQuery(WITH_CONTEXT + READABLE + " and q.questionId = :questionId", QuestionVersion.class)
                .setParameter("readAllowed", readAllowed).setParameter("now", now).setParameter("questionId", questionId)
                .getResultList().stream().findFirst();
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> query, boolean readAllowed, OffsetDateTime now,
                                  Integer partId, Integer topicId, String difficulty) {
        return query.setParameter("readAllowed", readAllowed).setParameter("now", now)
                .setParameter("partId", partId).setParameter("topicId", topicId).setParameter("difficulty", difficulty);
    }
}
