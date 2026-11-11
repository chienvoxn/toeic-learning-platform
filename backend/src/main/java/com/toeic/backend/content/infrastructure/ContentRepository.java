package com.toeic.backend.content.infrastructure;

import com.toeic.backend.content.domain.LearningContent;
import com.toeic.backend.content.domain.LearningContentVersion;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends Repository<LearningContent, UUID> {
    LearningContent saveAndFlush(LearningContent content);

    String MANAGEMENT = " where (c.authorId=:actorId or :any=true)";
    String MANAGEMENT_FILTERS = " and (:category is null or cat.code=:category)"
            + " and (:state is null or c.status=:state)";

    @Query(value = "select c from LearningContent c join fetch c.category cat left join fetch c.currentPublishedVersion"
            + MANAGEMENT + MANAGEMENT_FILTERS,
            countQuery = "select count(c) from LearningContent c join c.category cat" + MANAGEMENT + MANAGEMENT_FILTERS)
    Page<LearningContent> findManaged(@Param("actorId") UUID actorId, @Param("any") boolean any,
            @Param("category") String category, @Param("state") String state, Pageable pageable);

    @Query("select c from LearningContent c join fetch c.category left join fetch c.currentPublishedVersion"
            + MANAGEMENT + " and c.contentId=:id")
    Optional<LearningContent> findManagedById(@Param("id") UUID id, @Param("actorId") UUID actorId,
            @Param("any") boolean any);

    // Same parent-before-version order as CONTENT-02; acquire the write lock before inspecting drafts.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LearningContent c" + MANAGEMENT + " and c.contentId=:id")
    Optional<LearningContent> lockManaged(@Param("id") UUID id, @Param("actorId") UUID actorId,
            @Param("any") boolean any);

    String VISIBLE = """
             where c.status = 'PUBLISHED' and v.status = 'PUBLISHED'
               and v.contentId = c.contentId and v.archivedAt is null
               and v.publishedAt is not null and v.publishedAt <= :now
               and (c.visibility = 'PUBLIC' or (:member = true and c.visibility = 'AUTHENTICATED'))
            """;
    String FILTERS = """
               and (:categoryId is null or cat.categoryId = :categoryId)
               and (:difficulty is null or c.difficulty = :difficulty)
               and (cast(:search as String) is null
                    or lower(collate(v.title as pg_c_utf8)) like lower(collate(cast(:search as String) as pg_c_utf8)) escape '!'
                    or lower(collate(v.summary as pg_c_utf8)) like lower(collate(cast(:search as String) as pg_c_utf8)) escape '!'
                    or lower(collate(v.body as pg_c_utf8)) like lower(collate(cast(:search as String) as pg_c_utf8)) escape '!')
            """;

    // Fetch only to-one associations before pagination; count uses the identical visibility predicate.
    @Query(value = "select c from LearningContent c join fetch c.category cat join fetch c.currentPublishedVersion v"
            + VISIBLE + FILTERS,
            countQuery = "select count(c) from LearningContent c join c.category cat join c.currentPublishedVersion v"
                    + VISIBLE + FILTERS)
    Page<LearningContent> findReadable(@Param("member") boolean member, @Param("now") OffsetDateTime now,
                                      @Param("categoryId") Integer categoryId, @Param("difficulty") String difficulty,
                                      @Param("search") String search, Pageable pageable);

    @Query("select c from LearningContent c join fetch c.category cat join fetch c.currentPublishedVersion v"
            + VISIBLE + " and c.contentId = :contentId")
    Optional<LearningContent> findReadableById(@Param("contentId") UUID contentId, @Param("member") boolean member,
                                              @Param("now") OffsetDateTime now);

    // Lock the identity first, before joining its pointer, to avoid a mixed read during republish.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from LearningContent c where c.contentId = :contentId")
    Optional<LearningContent> lockIdentity(@Param("contentId") UUID contentId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select v from LearningContentVersion v where v.contentVersionId = :versionId")
    Optional<LearningContentVersion> lockVersion(@Param("versionId") UUID versionId);
}
