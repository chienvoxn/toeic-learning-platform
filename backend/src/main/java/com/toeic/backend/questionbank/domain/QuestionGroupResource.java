package com.toeic.backend.questionbank.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "question_group_version_resources")
public class QuestionGroupResource {
    @EmbeddedId
    @AttributeOverride(name = "versionId", column = @Column(name = "group_version_id"))
    private VersionAssetId id;
    @MapsId("versionId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_version_id", nullable = false)
    private QuestionGroupVersion version;
    @Column(name = "display_order", nullable = false) private int displayOrder;

    protected QuestionGroupResource() { }
    QuestionGroupResource(QuestionGroupVersion version, UUID resourceId, int displayOrder) {
        this.version = version;
        this.id = new VersionAssetId(version.getGroupVersionId(), resourceId);
        this.displayOrder = QuestionValues.positive(displayOrder, "resource displayOrder");
    }
    public UUID getResourceId() { return id.getResourceId(); }
    public UUID getGroupVersionId() { return id.getVersionId(); }
    public int getDisplayOrder() { return displayOrder; }
}
