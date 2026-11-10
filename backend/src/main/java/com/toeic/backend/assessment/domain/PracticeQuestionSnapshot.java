package com.toeic.backend.assessment.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attempt_question_snapshots")
public class PracticeQuestionSnapshot {
    @Id @Column(name = "snapshot_id") private UUID snapshotId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false) private PracticeAttempt attempt;
    @Column(name = "question_version_id", nullable = false) private UUID questionVersionId;
    @Column(name = "question_group_version_id") private UUID groupVersionId;
    @Column(name = "question_order", nullable = false) private int questionOrder;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    protected PracticeQuestionSnapshot() { }
    PracticeQuestionSnapshot(PracticeAttempt attempt, PracticeAttempt.Pin pin, int order, OffsetDateTime time) {
        snapshotId = UUID.randomUUID();
        this.attempt = attempt;
        questionVersionId = pin.questionVersionId();
        groupVersionId = pin.groupVersionId();
        questionOrder = order;
        createdAt = time;
    }
    public UUID getQuestionVersionId() { return questionVersionId; }
    public UUID getGroupVersionId() { return groupVersionId; }
    public int getQuestionOrder() { return questionOrder; }
}
