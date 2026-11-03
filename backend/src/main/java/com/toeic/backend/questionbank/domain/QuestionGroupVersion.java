package com.toeic.backend.questionbank.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Entity
@Table(name = "question_group_versions")
public class QuestionGroupVersion {
    @Id @Column(name = "group_version_id") private UUID groupVersionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false) private QuestionGroup group;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "published_at") private OffsetDateTime publishedAt;
    @Column(name = "archived_at") private OffsetDateTime archivedAt;
    @Column(columnDefinition = "TEXT") private String instruction;
    @Column(name = "passage_text", columnDefinition = "TEXT") private String passageText;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @OneToMany(mappedBy = "version", cascade = CascadeType.PERSIST)
    private Set<QuestionGroupResource> resources = new HashSet<>();
    @Transient private boolean assembling = true;

    protected QuestionGroupVersion() { }
    public QuestionGroupVersion(QuestionGroup group, int versionNumber, String instruction, String passageText) {
        this.groupVersionId = UUID.randomUUID();
        this.status = "DRAFT";
        this.group = QuestionValues.required(group, "group");
        this.versionNumber = QuestionValues.positive(versionNumber, "versionNumber");
        this.instruction = instruction;
        this.passageText = passageText;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
    public void addResource(UUID resourceId, int order) {
        if (!assembling) { throw new IllegalStateException("Group version is a persisted snapshot"); }
        if (resources.stream().anyMatch(resource -> resource.getResourceId().equals(resourceId))) {
            throw new IllegalArgumentException("Duplicate group resource");
        }
        resources.add(new QuestionGroupResource(this, resourceId, order));
    }
    @PostPersist @PostLoad
    private void freeze() { assembling = false; }

    public UUID getGroupVersionId() { return groupVersionId; }
    public QuestionGroup getGroup() { return group; }
    public int getVersionNumber() { return versionNumber; }
    public String getStatus() { return status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public String getInstruction() { return instruction; }
    public String getPassageText() { return passageText; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<QuestionGroupResource> getResources() {
        return resources.stream().sorted(Comparator.comparingInt(QuestionGroupResource::getDisplayOrder)
                .thenComparing(resource -> resource.getResourceId().toString())).toList();
    }
}
