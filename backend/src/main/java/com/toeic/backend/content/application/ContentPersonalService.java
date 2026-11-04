package com.toeic.backend.content.application;

import com.toeic.backend.content.infrastructure.ContentBookmarkRepository;
import com.toeic.backend.content.infrastructure.ContentHistoryRepository;
import com.toeic.backend.content.infrastructure.ContentRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentPersonalService {
    private final ContentRepository contents;
    private final ContentBookmarkRepository bookmarks;
    private final ContentHistoryRepository history;
    private final ContentAccessPolicy access;
    private final Clock clock;

    public ContentPersonalService(ContentRepository contents, ContentBookmarkRepository bookmarks,
                                   ContentHistoryRepository history, ContentAccessPolicy access, Clock clock) {
        this.contents = contents;
        this.bookmarks = bookmarks;
        this.history = history;
        this.access = access;
        this.clock = clock;
    }

    private UUID lockReadableVersion(UUID contentId) {
        var identity = contents.lockIdentity(contentId).orElseThrow(ContentException::notFound);
        if (identity.getCurrentPublishedVersion() == null) { throw ContentException.notFound(); }
        contents.lockVersion(identity.getCurrentPublishedVersion().getContentVersionId()).orElseThrow(ContentException::notFound);
        var content = contents.findReadableById(contentId, access.canReadMemberContent(), OffsetDateTime.now(clock))
                .orElseThrow(ContentException::notFound);
        return content.getCurrentPublishedVersion().getContentVersionId();
    }

    @Transactional
    @PreAuthorize("hasAuthority('content:bookmark:self') and @contentAccessPolicy.isActor(#userId)")
    public PersonalContentDtos.BookmarkReceipt bookmark(UUID userId, UUID contentId) {
        lockReadableVersion(contentId);
        var now = OffsetDateTime.now(clock);
        if (bookmarks.insertIfAbsent(userId, contentId, now) == 0) { throw ContentException.bookmarkExists(); }
        return new PersonalContentDtos.BookmarkReceipt(contentId, now);
    }

    @Transactional
    @PreAuthorize("hasAuthority('content:bookmark:self') and @contentAccessPolicy.isActor(#userId)")
    public void removeBookmark(UUID userId, UUID contentId) {
        if (bookmarks.deleteOwned(userId, contentId) == 0) { throw ContentException.bookmarkNotFound(); }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('content:bookmark:self') and @contentAccessPolicy.isActor(#userId)")
    public PersonalContentDtos.Page<PersonalContentDtos.Bookmark> bookmarks(UUID userId, PersonalContentQuery query) {
        query.requireKind(PersonalContentQuery.Kind.BOOKMARKS);
        var page = bookmarks.findReadable(userId, access.canReadMemberContent(), OffsetDateTime.now(clock), query.pageable());
        return new PersonalContentDtos.Page<>(page.getContent().stream().map(row ->
                new PersonalContentDtos.Bookmark(ContentDtos.Summary.from(row.getContent()), row.getCreatedAt())).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    @PreAuthorize("hasAuthority('content:history:self') and @contentAccessPolicy.isActor(#userId)")
    public void recordHistory(UUID userId, UUID contentId) {
        var version = lockReadableVersion(contentId);
        history.record(userId, contentId, version, OffsetDateTime.now(clock));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('content:history:self') and @contentAccessPolicy.isActor(#userId)")
    public PersonalContentDtos.Page<PersonalContentDtos.History> history(UUID userId, PersonalContentQuery query) {
        query.requireKind(PersonalContentQuery.Kind.HISTORY);
        var page = history.findReadable(userId, access.canReadMemberContent(), OffsetDateTime.now(clock), query.pageable());
        return new PersonalContentDtos.Page<>(page.getContent().stream().map(row ->
                new PersonalContentDtos.History(ContentDtos.Summary.from(row.getContent()), row.getLastAccessedAt())).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
