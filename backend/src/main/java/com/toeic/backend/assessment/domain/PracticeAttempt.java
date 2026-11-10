package com.toeic.backend.assessment.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Dynamic practice owns an immutable, ordered set of version references. No answer/grading mutation. */
@Entity
@Table(name = "attempts")
public class PracticeAttempt {
    @Id @Column(name = "attempt_id") private UUID attemptId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "test_version_id") private UUID testVersionId;
    @Column(name = "attempt_type", nullable = false) private String attemptType;
    @Column(name = "attempt_source_type", nullable = false) private String sourceType;
    @Column(nullable = false) private String status;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "total_questions") private Integer totalQuestions;
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.PERSIST)
    @OrderBy("questionOrder ASC") private List<PracticeQuestionSnapshot> questions = new ArrayList<>();

    protected PracticeAttempt() { }
    public record Pin(UUID questionVersionId, UUID groupVersionId) {
        public Pin { Objects.requireNonNull(questionVersionId, "questionVersionId"); }
    }
    public PracticeAttempt(UUID actor, OffsetDateTime started, OffsetDateTime expiry, List<Pin> pins) {
        userId = Objects.requireNonNull(actor, "actor");
        startedAt = Objects.requireNonNull(started, "started");
        expiresAt = Objects.requireNonNull(expiry, "expiry");
        if (!expiry.isAfter(started) || pins == null || pins.isEmpty() || pins.stream().anyMatch(Objects::isNull)
                || pins.stream().map(Pin::questionVersionId).distinct().count() != pins.size()) {
            throw new IllegalArgumentException("Invalid practice snapshot");
        }
        attemptId = UUID.randomUUID();
        attemptType = "PRACTICE";
        sourceType = "PRACTICE_SNAPSHOT";
        status = "IN_PROGRESS";
        createdAt = started;
        totalQuestions = pins.size();
        for (int i = 0; i < pins.size(); i++) {
            questions.add(new PracticeQuestionSnapshot(this, pins.get(i), i + 1, started));
        }
    }
    public UUID getAttemptId() { return attemptId; }
    public UUID getUserId() { return userId; }
    public UUID getTestVersionId() { return testVersionId; }
    public String getSourceType() { return sourceType; }
    public String getStatus() { return status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public Integer getTotalQuestions() { return totalQuestions; }
    public List<PracticeQuestionSnapshot> getQuestions() { return List.copyOf(questions); }
}
