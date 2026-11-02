package com.toeic.backend.content.application;

import com.toeic.backend.content.domain.ContentCategory;
import com.toeic.backend.content.domain.ContentVersionResource;
import com.toeic.backend.content.domain.LearningContent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ContentDtos {
    private ContentDtos() { }

    public record Category(Integer categoryId, String code, String name) {
        static Category from(ContentCategory category) {
            return new Category(category.getCategoryId(), category.getCode(), category.getName());
        }
    }
    public record Summary(UUID contentId, UUID contentVersionId, int versionNumber, Category category,
                          String title, String summary, String difficulty, OffsetDateTime publishedAt) {
        static Summary from(LearningContent content) {
            var version = content.getCurrentPublishedVersion();
            return new Summary(content.getContentId(), version.getContentVersionId(), version.getVersionNumber(),
                    Category.from(content.getCategory()), version.getTitle(), version.getSummary(),
                    content.getDifficulty(), version.getPublishedAt());
        }
    }
    public record Media(UUID resourceId, String resourceType, String mimeType, Long fileSize,
                        Integer durationSeconds, int displayOrder) {
        static Media from(ContentVersionResource link) {
            var resource = link.getResource();
            return new Media(resource.getResourceId(), resource.getResourceType(), resource.getMimeType(),
                    resource.getFileSize(), resource.getDurationSeconds(), link.getDisplayOrder());
        }
    }
    public record Detail(UUID contentId, UUID contentVersionId, int versionNumber, Category category,
                         String title, String summary, String body, String difficulty,
                         OffsetDateTime publishedAt, List<Media> media) {
        public Detail { media = List.copyOf(media); }
        static Detail from(LearningContent content, List<ContentVersionResource> resources) {
            var version = content.getCurrentPublishedVersion();
            return new Detail(content.getContentId(), version.getContentVersionId(), version.getVersionNumber(),
                    Category.from(content.getCategory()), version.getTitle(), version.getSummary(), version.getBody(),
                    content.getDifficulty(), version.getPublishedAt(), resources.stream().map(Media::from).toList());
        }
    }
    public record ContentPage(List<Summary> content, int page, int size, long totalElements, int totalPages) {
        public ContentPage { content = List.copyOf(content); }
    }
}
