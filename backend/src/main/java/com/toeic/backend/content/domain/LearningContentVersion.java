package com.toeic.backend.content.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "learning_content_versions")
public class LearningContentVersion {
    @Id
    @Column(name = "content_version_id")
    private UUID contentVersionId;
    @Column(name = "content_id", nullable = false)
    private UUID contentId;
    @Column(name = "version_number", nullable = false)
    private int versionNumber;
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
    public UUID getContentVersionId() { return contentVersionId; }
    public int getVersionNumber() { return versionNumber; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getBody() { return body; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
}
