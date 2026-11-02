package com.toeic.backend.content.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "learning_content_version_resources")
public class ContentVersionResource {
    @EmbeddedId
    private Id id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", insertable = false, updatable = false)
    private MediaResource resource;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ContentVersionResource() { }
    public MediaResource getResource() { return resource; }
    public int getDisplayOrder() { return displayOrder; }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "content_version_id")
        private UUID contentVersionId;
        @Column(name = "resource_id")
        private UUID resourceId;

        protected Id() { }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Id that
                    && Objects.equals(contentVersionId, that.contentVersionId)
                    && Objects.equals(resourceId, that.resourceId);
        }
        @Override public int hashCode() { return Objects.hash(contentVersionId, resourceId); }
    }
}
