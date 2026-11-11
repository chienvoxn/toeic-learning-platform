package com.toeic.backend.assessment.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Practice giữ pin bất biến; trạng thái/kết quả chỉ chốt khi caller giữ khóa attempt. */
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
    @Column(name = "submitted_at") private OffsetDateTime submittedAt;
    @Column(name = "duration_seconds") private Integer durationSeconds;
    @Column(name = "correct_count") private Integer correctCount;
    @Column(name = "listening_correct_count") private Integer listeningCorrectCount;
    @Column(name = "reading_correct_count") private Integer readingCorrectCount;
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
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public Integer getCorrectCount() { return correctCount; }
    public Integer getListeningCorrectCount() { return listeningCorrectCount; }
    public Integer getReadingCorrectCount() { return readingCorrectCount; }
    public void completeGrading(OffsetDateTime submitted, int correct, int listening, int reading) {
        if (!"IN_PROGRESS".equals(status) || submitted.isBefore(startedAt) || !submitted.isBefore(expiresAt)
                || correct < 0 || correct > totalQuestions || listening < 0 || reading < 0 || listening + reading != correct) {
            throw new IllegalStateException("Invalid practice grading transition");
        }
        submittedAt = submitted;
        durationSeconds = Math.toIntExact(java.time.temporal.ChronoUnit.SECONDS.between(startedAt, submitted));
        correctCount = correct;
        listeningCorrectCount = listening;
        readingCorrectCount = reading;
        status = "GRADED";
    }
    public List<PracticeQuestionSnapshot> getQuestions() { return List.copyOf(questions); }
}
