
package com.toeic.backend.vocabulary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "review_history")
public class ReviewHistory {

    @Id
    @Column(name = "review_history_id", nullable = false)
    private UUID reviewHistoryId;

    @Column(name = "review_state_id", nullable = false)
    private UUID reviewStateId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "previous_interval")
    private Integer previousInterval;

    @Column(name = "new_interval")
    private Integer newInterval;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    protected ReviewHistory() {
    }

    public ReviewHistory(
            UUID reviewStateId,
            int rating,
            Integer previousInterval,
            Integer newInterval
    ) {
        if (reviewStateId == null) {
            throw new IllegalArgumentException(
                "Review state ID must not be null"
            );
        }

        if (rating < 1 || rating > 4) {
            throw new IllegalArgumentException(
                "Rating must be between 1 and 4"
            );
        }

        if (previousInterval != null && previousInterval < 0) {
            throw new IllegalArgumentException(
                "Previous interval must be non-negative"
            );
        }

        if (newInterval != null && newInterval < 0) {
            throw new IllegalArgumentException(
                "New interval must be non-negative"
            );
        }

        this.reviewHistoryId = UUID.randomUUID();
        this.reviewStateId = reviewStateId;
        this.rating = rating;
        this.previousInterval = previousInterval;
        this.newInterval = newInterval;
        this.reviewedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    protected void onCreate() {
        if (reviewHistoryId == null) {
            reviewHistoryId = UUID.randomUUID();
        }

        if (reviewedAt == null) {
            reviewedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public UUID getReviewHistoryId() {
        return reviewHistoryId;
    }

    public UUID getReviewStateId() {
        return reviewStateId;
    }

    public int getRating() {
        return rating;
    }

    public Integer getPreviousInterval() {
        return previousInterval;
    }

    public Integer getNewInterval() {
        return newInterval;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
}
