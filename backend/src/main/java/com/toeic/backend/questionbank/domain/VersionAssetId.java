package com.toeic.backend.questionbank.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class VersionAssetId implements Serializable {
    @Column(name = "version_id") private UUID versionId;
    @Column(name = "resource_id") private UUID resourceId;
    protected VersionAssetId() { }
    public VersionAssetId(UUID versionId, UUID resourceId) {
        this.versionId = QuestionValues.required(versionId, "versionId");
        this.resourceId = QuestionValues.required(resourceId, "resourceId");
    }
    public UUID getVersionId() { return versionId; }
    public UUID getResourceId() { return resourceId; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof VersionAssetId that
                && Objects.equals(versionId, that.versionId) && Objects.equals(resourceId, that.resourceId);
    }
    @Override public int hashCode() { return Objects.hash(versionId, resourceId); }
}
