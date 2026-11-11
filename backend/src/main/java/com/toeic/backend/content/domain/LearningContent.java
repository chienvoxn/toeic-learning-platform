package com.toeic.backend.content.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "learning_contents")
public class LearningContent {
    @Id
    @Column(name = "content_id")
    private UUID contentId;
    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    // Legacy required columns are populated on insert only. Version text is authoritative.
    @Column(name = "title", nullable = false, updatable = false, length = 255)
    private String legacyTitle;
    @Column(name = "summary", updatable = false, columnDefinition = "TEXT")
    private String legacySummary;
    @Column(name = "body", updatable = false, columnDefinition = "TEXT")
    private String legacyBody;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ContentCategory category;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_published_version_id")
    private LearningContentVersion currentPublishedVersion;
    @Column(length = 20)
    private String difficulty;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, length = 20)
    private String visibility;

    protected LearningContent() { }

    public LearningContent(UUID actorId, ContentCategory category, String title, String summary,
                           String body, String difficulty, OffsetDateTime now) {
        this.contentId = UUID.randomUUID();
        this.authorId = java.util.Objects.requireNonNull(actorId);
        this.category = java.util.Objects.requireNonNull(category);
        this.legacyTitle = title;
        this.legacySummary = summary;
        this.legacyBody = body;
        this.difficulty = difficulty;
        this.status = "DRAFT";
        this.visibility = "PRIVATE";
        this.createdAt = java.util.Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public void editDraftMetadata(ContentCategory category, String difficulty) {
        if (!"DRAFT".equals(status)) { throw new IllegalStateException("Content metadata is not a draft"); }
        this.category = java.util.Objects.requireNonNull(category);
        this.difficulty = difficulty;
    }

    public void touch(OffsetDateTime now) { this.updatedAt = java.util.Objects.requireNonNull(now); }

    public void archive(OffsetDateTime now) {
        if (!"ARCHIVED".equals(status)) { this.status = "ARCHIVED"; touch(now); }
    }
    public UUID getContentId() { return contentId; }
    public UUID getAuthorId() { return authorId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getStatus() { return status; }
    public String getVisibility() { return visibility; }
    public ContentCategory getCategory() { return category; }
    public LearningContentVersion getCurrentPublishedVersion() { return currentPublishedVersion; }
    public String getDifficulty() { return difficulty; }
}
