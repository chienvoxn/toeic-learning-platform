package com.toeic.backend.content.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "learning_content_versions")
@DynamicUpdate
public class LearningContentVersion {
    @Id
    @Column(name = "content_version_id")
    private UUID contentVersionId;
    @Column(name = "content_id", nullable = false, updatable = false)
    private UUID contentId;
    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String summary;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    protected LearningContentVersion() { }

    /** UC-12: tạo draft; actor, parent và số version do application xác định, không lấy từ payload. */
    public LearningContentVersion(UUID contentId, int versionNumber, UUID actorId, String title,
                                  String summary, String body, OffsetDateTime now) {
        if (contentId == null || actorId == null || versionNumber < 1 || now == null) {
            throw new IllegalArgumentException("Invalid version identity or timestamp");
        }
        this.contentVersionId = UUID.randomUUID();
        this.contentId = contentId;
        this.versionNumber = versionNumber;
        this.createdBy = actorId;
        this.createdAt = now;
        this.status = "DRAFT";
        replaceDraftContent(title, summary, body, now);
    }

    public void replaceDraftContent(String title, String summary, String body, OffsetDateTime now) {
        if (!"DRAFT".equals(status) || publishedAt != null || archivedAt != null) {
            throw new IllegalStateException("Only an unpublished draft version can be edited");
        }
        if (title == null || title.isBlank() || title.length() > 255 || body == null || now == null) {
            throw new IllegalArgumentException("Invalid draft content");
        }
        this.title = title.strip();
        this.summary = summary;
        this.body = body;
        this.updatedAt = now;
    }

    public UUID getContentVersionId() { return contentVersionId; }
    public UUID getContentId() { return contentId; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getStatus() { return status; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public int getVersionNumber() { return versionNumber; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getBody() { return body; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
}
