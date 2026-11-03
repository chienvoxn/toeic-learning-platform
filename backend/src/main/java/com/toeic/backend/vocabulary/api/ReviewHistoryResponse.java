
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.vocabulary.domain.ReviewHistory;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewHistoryResponse(
        UUID reviewHistoryId,
        int rating,
        Integer previousInterval,
        Integer newInterval,
        OffsetDateTime reviewedAt
) {
    public static ReviewHistoryResponse from(
            ReviewHistory history
    ) {
        return new ReviewHistoryResponse(
                history.getReviewHistoryId(),
                history.getRating(),
                history.getPreviousInterval(),
                history.getNewInterval(),
                history.getReviewedAt()
        );
    }
}
