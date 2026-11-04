
package com.toeic.backend.vocabulary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
    name = "review_states",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_review_state_user_vocabulary",
        columnNames = {"user_id", "vocabulary_id"}
    )
)
public class ReviewState {

    @Id
    @Column(name = "review_state_id", nullable = false)
    private UUID reviewStateId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vocabulary_id", nullable = false)
    private UUID vocabularyId;

    @Column(name = "repetition_count", nullable = false)
    private int repetitionCount = 0;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays = 0;

    @Column(name = "ease_factor", precision = 4, scale = 2)
    private BigDecimal easeFactor = new BigDecimal("2.50");

    @Column(name = "last_reviewed_at")
    private OffsetDateTime lastReviewedAt;

    @Column(name = "next_review_at", nullable = false)
    private OffsetDateTime nextReviewAt;

    protected ReviewState() {
    }

    public ReviewState(UUID userId, UUID vocabularyId) {
        if (userId == null || vocabularyId == null) {
            throw new IllegalArgumentException(
                "User ID and vocabulary ID must not be null"
            );
        }

        this.reviewStateId = UUID.randomUUID();
        this.userId = userId;
        this.vocabularyId = vocabularyId;
        this.nextReviewAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    protected void onCreate() {
        if (reviewStateId == null) {
            reviewStateId = UUID.randomUUID();
        }

        if (nextReviewAt == null) {
            nextReviewAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public void updateSchedule(
            int repetitionCount,
            int intervalDays,
            BigDecimal easeFactor,
            OffsetDateTime reviewedAt,
            OffsetDateTime nextReviewAt
    ) {
        if (repetitionCount < 0 || intervalDays < 0) {
            throw new IllegalArgumentException(
                "Repetition count and interval days must be non-negative"
            );
        }

        if (easeFactor == null || easeFactor.signum() <= 0) {
            throw new IllegalArgumentException(
                "Ease factor must be positive"
            );
        }

        if (easeFactor.scale() > 2
                || easeFactor.precision() - easeFactor.scale() > 2) {
            throw new IllegalArgumentException(
                "Ease factor must fit DECIMAL(4,2)"
            );
        }

        if (reviewedAt == null || nextReviewAt == null) {
            throw new IllegalArgumentException(
                "Review timestamps must not be null"
            );
        }

        if (nextReviewAt.isBefore(reviewedAt)) {
            throw new IllegalArgumentException(
                "Next review must not be before the current review"
            );
        }

        this.repetitionCount = repetitionCount;
        this.intervalDays = intervalDays;
        this.easeFactor = easeFactor;
        this.lastReviewedAt = reviewedAt;
        this.nextReviewAt = nextReviewAt;
    }

    public UUID getReviewStateId() {
        return reviewStateId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getVocabularyId() {
        return vocabularyId;
    }

    public int getRepetitionCount() {
        return repetitionCount;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public BigDecimal getEaseFactor() {
        return easeFactor;
    }

    public OffsetDateTime getLastReviewedAt() {
        return lastReviewedAt;
    }

    public OffsetDateTime getNextReviewAt() {
        return nextReviewAt;
    }
}
