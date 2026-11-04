package com.toeic.backend.content.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "media_resources")
public class MediaResource {
    @Id
    @Column(name = "resource_id")
    private UUID resourceId;
    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;
    @Column(name = "resource_type", nullable = false, length = 30)
    private String resourceType;
    @Column(name = "mime_type", length = 100)
    private String mimeType;
    @Column(name = "file_size")
    private Long fileSize;
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
    @Column(nullable = false, length = 20)
    private String visibility;

    protected MediaResource() { }
    public UUID getResourceId() { return resourceId; }
    public UUID getOwnerId() { return ownerId; }
    public String getResourceType() { return resourceType; }
    public String getMimeType() { return mimeType; }
    public Long getFileSize() { return fileSize; }
    public Integer getDurationSeconds() { return durationSeconds; }
}
