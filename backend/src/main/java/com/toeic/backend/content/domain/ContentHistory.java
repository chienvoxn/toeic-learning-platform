package com.toeic.backend.content.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "learning_content_history")
public class ContentHistory {
    @EmbeddedId
    private Id id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", insertable = false, updatable = false)
    private LearningContent content;
    @Column(name = "content_version_id", nullable = false)
    private UUID contentVersionId;
    @Column(name = "last_accessed_at", nullable = false)
    private OffsetDateTime lastAccessedAt;

    protected ContentHistory() { }
    public LearningContent getContent() { return content; }
    public OffsetDateTime getLastAccessedAt() { return lastAccessedAt; }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "user_id", nullable = false)
        private UUID userId;
        @Column(name = "content_id", nullable = false)
        private UUID contentId;
        protected Id() { }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Id that
                    && Objects.equals(userId, that.userId) && Objects.equals(contentId, that.contentId);
        }
        @Override public int hashCode() { return Objects.hash(userId, contentId); }
    }
}
