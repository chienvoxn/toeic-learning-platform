package com.toeic.backend.content.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "learning_contents")
public class LearningContent {
    @Id
    @Column(name = "content_id")
    private UUID contentId;
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
    public UUID getContentId() { return contentId; }
    public ContentCategory getCategory() { return category; }
    public LearningContentVersion getCurrentPublishedVersion() { return currentPublishedVersion; }
    public String getDifficulty() { return difficulty; }
}
