package com.toeic.backend.content.application;

import com.toeic.backend.content.infrastructure.ContentMediaRepository;
import com.toeic.backend.content.infrastructure.ContentRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Đọc phiên bản được phép, phân trang/count nhất quán và chuyển sang DTO trong transaction. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ContentReadService {
    private final ContentRepository contents;
    private final ContentMediaRepository media;
    private final ContentAccessPolicy access;
    private final Clock clock;

    public ContentReadService(ContentRepository contents, ContentMediaRepository media,
                              ContentAccessPolicy access, Clock clock) {
        this.contents = contents;
        this.media = media;
        this.access = access;
        this.clock = clock;
    }

    public ContentDtos.ContentPage list(ContentQuery query) {
        var page = contents.findReadable(access.canReadMemberContent(), OffsetDateTime.now(clock),
                query.categoryId(), query.difficulty(), query.pageable());
        return new ContentDtos.ContentPage(page.getContent().stream().map(ContentDtos.Summary::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public ContentDtos.Detail detail(UUID contentId) {
        boolean member = access.canReadMemberContent();
        var content = contents.findReadableById(contentId, member, OffsetDateTime.now(clock))
                .orElseThrow(ContentException::notFound);
        return ContentDtos.Detail.from(content,
                media.findReadable(content.getCurrentPublishedVersion().getContentVersionId(), member));
    }
}
