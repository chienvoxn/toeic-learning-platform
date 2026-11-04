package com.toeic.backend.questionbank.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "question_groups")
public class QuestionGroup {
    @Id @Column(name = "group_id") private UUID groupId;
    @Column(name = "current_published_version_id") private UUID currentPublishedVersionId;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "group_type", length = 30) private String groupType;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected QuestionGroup() { }
    public QuestionGroup(String groupType) {
        this.groupId = UUID.randomUUID();
        this.status = "DRAFT";
        this.groupType = QuestionValues.optional(groupType, 30, "group type");
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }
    public UUID getGroupId() { return groupId; }
    public UUID getCurrentPublishedVersionId() { return currentPublishedVersionId; }
    public String getStatus() { return status; }
    public String getGroupType() { return groupType; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
