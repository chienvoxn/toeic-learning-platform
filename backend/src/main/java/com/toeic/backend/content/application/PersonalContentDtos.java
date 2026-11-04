package com.toeic.backend.content.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PersonalContentDtos {
    private PersonalContentDtos() { }
    public record BookmarkReceipt(UUID contentId, OffsetDateTime createdAt) { }
    public record Bookmark(ContentDtos.Summary content, OffsetDateTime createdAt) { }
    public record History(ContentDtos.Summary content, OffsetDateTime lastAccessedAt) { }
    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public Page { content = List.copyOf(content); }
    }
}
