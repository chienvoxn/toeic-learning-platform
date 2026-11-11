package com.toeic.backend.content.infrastructure;

import com.toeic.backend.content.domain.ContentHistory;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ContentHistoryRepository extends Repository<ContentHistory, ContentHistory.Id> {
    @Modifying
    @Query(value = """
            insert into learning_content_history (user_id,content_id,content_version_id,last_accessed_at)
            values (:userId,:contentId,:versionId,:now)
            on conflict (user_id,content_id) do update
            set content_version_id=excluded.content_version_id,
                last_accessed_at=greatest(learning_content_history.last_accessed_at,excluded.last_accessed_at)
            """, nativeQuery = true)
    int record(@Param("userId") UUID userId, @Param("contentId") UUID contentId,
               @Param("versionId") UUID versionId, @Param("now") OffsetDateTime now);

    @Query(value = """
            select h from ContentHistory h join fetch h.content c
            join fetch c.category cat join fetch c.currentPublishedVersion v
            """ + ContentRepository.VISIBLE + " and h.id.userId=:userId and h.contentVersionId=v.contentVersionId",
            countQuery = """
            select count(h) from ContentHistory h join h.content c
            join c.category cat join c.currentPublishedVersion v
            """ + ContentRepository.VISIBLE + " and h.id.userId=:userId and h.contentVersionId=v.contentVersionId")
    Page<ContentHistory> findReadable(@Param("userId") UUID userId, @Param("member") boolean member,
                                     @Param("now") OffsetDateTime now, Pageable pageable);
}
