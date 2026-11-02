package com.toeic.backend.content.infrastructure;

import com.toeic.backend.content.domain.LearningContent;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends Repository<LearningContent, UUID> {
    String VISIBLE = """
             where c.status = 'PUBLISHED' and v.status = 'PUBLISHED'
               and v.contentId = c.contentId and v.archivedAt is null
               and v.publishedAt is not null and v.publishedAt <= :now
               and (c.visibility = 'PUBLIC' or (:member = true and c.visibility = 'AUTHENTICATED'))
            """;
    String FILTERS = """
               and (:categoryId is null or cat.categoryId = :categoryId)
               and (:difficulty is null or c.difficulty = :difficulty)
            """;

    // Fetch only to-one associations before pagination; count uses the identical visibility predicate.
    @Query(value = "select c from LearningContent c join fetch c.category cat join fetch c.currentPublishedVersion v"
            + VISIBLE + FILTERS,
            countQuery = "select count(c) from LearningContent c join c.category cat join c.currentPublishedVersion v"
                    + VISIBLE + FILTERS)
    Page<LearningContent> findReadable(@Param("member") boolean member, @Param("now") OffsetDateTime now,
                                      @Param("categoryId") Integer categoryId, @Param("difficulty") String difficulty,
                                      Pageable pageable);

    @Query("select c from LearningContent c join fetch c.category cat join fetch c.currentPublishedVersion v"
            + VISIBLE + " and c.contentId = :contentId")
    Optional<LearningContent> findReadableById(@Param("contentId") UUID contentId, @Param("member") boolean member,
                                              @Param("now") OffsetDateTime now);
}
