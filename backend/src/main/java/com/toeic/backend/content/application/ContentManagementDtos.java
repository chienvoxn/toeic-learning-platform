package com.toeic.backend.content.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ContentManagementDtos {
    private ContentManagementDtos() { }
    public record Summary(UUID contentId, ContentDtos.Category category, String title, String difficulty,
                          String status, String visibility, UUID publishedVersionId, UUID workingDraftVersionId,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
    public record Version(UUID contentVersionId, int versionNumber, String status, String title, String summary,
                          String body, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                          OffsetDateTime publishedAt, OffsetDateTime archivedAt, List<ContentDtos.Media> media) {
        public Version { media = List.copyOf(media); }
    }
    public record Detail(Summary content, List<Version> versions) {
        public Detail { versions = List.copyOf(versions); }
    }
    public record Page(List<Summary> content, int page, int size, long totalElements, int totalPages) {
        public Page { content = List.copyOf(content); }
    }
}
