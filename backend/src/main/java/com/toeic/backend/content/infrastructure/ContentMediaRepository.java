package com.toeic.backend.content.infrastructure;

import com.toeic.backend.content.domain.ContentVersionResource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ContentMediaRepository extends Repository<ContentVersionResource, ContentVersionResource.Id> {
    @Query("""
            select link from ContentVersionResource link join fetch link.resource media
            where link.id.contentVersionId = :versionId
              and (media.visibility = 'PUBLIC' or (:member = true and media.visibility = 'AUTHENTICATED'))
            order by link.displayOrder, media.resourceId
            """)
    List<ContentVersionResource> findReadable(@Param("versionId") UUID versionId, @Param("member") boolean member);
}
