package com.toeic.backend.questionbank.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "question_resources")
public class QuestionResource {
    @EmbeddedId
    @AttributeOverride(name = "versionId", column = @Column(name = "question_version_id"))
    private VersionAssetId id;
    @MapsId("versionId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_version_id", nullable = false)
    private QuestionVersion version;

    protected QuestionResource() { }
    QuestionResource(QuestionVersion version, UUID resourceId) {
        this.version = version;
        this.id = new VersionAssetId(version.getQuestionVersionId(), resourceId);
    }
    public UUID getResourceId() { return id.getResourceId(); }
    public UUID getQuestionVersionId() { return id.getVersionId(); }
}
