package com.toeic.backend.content.infrastructure;

import com.toeic.backend.content.domain.LearningContentVersion;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Persistence capability only. Application must authorize any-scope and lock content before numbering drafts. */
public interface ContentVersionRepository extends Repository<LearningContentVersion, UUID> {
    String MANAGEMENT_SCOPE = """
            from LearningContentVersion v, LearningContent c
            where v.contentId=c.contentId and (c.authorId=:actorId or :canManageAny=true)
            """;

    LearningContentVersion save(LearningContentVersion version);
    LearningContentVersion saveAndFlush(LearningContentVersion version);

    @Query("select v from LearningContentVersion v where v.contentId=:contentId order by v.versionNumber")
    List<LearningContentVersion> findAllForContent(@Param("contentId") UUID contentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from LearningContentVersion v where v.contentId=:contentId order by v.versionNumber")
    List<LearningContentVersion> lockAllForContent(@Param("contentId") UUID contentId);

    @Query("select v from LearningContentVersion v where v.contentId in :ids and v.status='DRAFT'")
    List<LearningContentVersion> findDrafts(@Param("ids") Collection<UUID> ids);

    @Query("select v " + MANAGEMENT_SCOPE + " and v.contentVersionId=:versionId")
    Optional<LearningContentVersion> findForManagement(@Param("versionId") UUID versionId,
            @Param("actorId") UUID actorId, @Param("canManageAny") boolean canManageAny);

    @Query(value = "select v " + MANAGEMENT_SCOPE + " and c.contentId=:contentId",
            countQuery = "select count(v) " + MANAGEMENT_SCOPE + " and c.contentId=:contentId")
    Page<LearningContentVersion> findVersionsForManagement(@Param("contentId") UUID contentId,
            @Param("actorId") UUID actorId, @Param("canManageAny") boolean canManageAny, Pageable pageable);
}
