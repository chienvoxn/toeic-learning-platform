package com.toeic.backend.content.infrastructure;

import com.toeic.backend.content.domain.ContentBookmark;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ContentBookmarkRepository extends Repository<ContentBookmark, ContentBookmark.Id> {
    @Modifying
    @Query(value = """
            insert into bookmarks (user_id, content_id, created_at) values (:userId, :contentId, :now)
            on conflict (user_id, content_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("contentId") UUID contentId,
                       @Param("now") OffsetDateTime now);

    @Modifying
    @Query("delete from ContentBookmark b where b.id.userId = :userId and b.id.contentId = :contentId")
    int deleteOwned(@Param("userId") UUID userId, @Param("contentId") UUID contentId);

    @Query(value = """
            select b from ContentBookmark b join fetch b.content c
            join fetch c.category cat join fetch c.currentPublishedVersion v
            """ + ContentRepository.VISIBLE + " and b.id.userId = :userId",
            countQuery = """
            select count(b) from ContentBookmark b join b.content c
            join c.category cat join c.currentPublishedVersion v
            """ + ContentRepository.VISIBLE + " and b.id.userId = :userId")
    Page<ContentBookmark> findReadable(@Param("userId") UUID userId, @Param("member") boolean member,
                                      @Param("now") OffsetDateTime now, Pageable pageable);
}
