
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.vocabulary.domain.ReviewState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewStateResponse(
        UUID reviewStateId,
        UUID vocabularyId,
        int repetitionCount,
        int intervalDays,
        BigDecimal easeFactor,
        OffsetDateTime lastReviewedAt,
        OffsetDateTime nextReviewAt
) {
    public static ReviewStateResponse from(ReviewState state) {
        return new ReviewStateResponse(
                state.getReviewStateId(),
                state.getVocabularyId(),
                state.getRepetitionCount(),
                state.getIntervalDays(),
                state.getEaseFactor(),
                state.getLastReviewedAt(),
                state.getNextReviewAt()
        );
    }
}
