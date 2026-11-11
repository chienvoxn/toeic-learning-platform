
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.domain.ReviewHistory;
import com.toeic.backend.vocabulary.domain.ReviewState;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class VocabularyFeatureExtractor {

    public VocabularyLearningFeatures extract(
            ReviewState state,
            List<ReviewHistory> history,
            OffsetDateTime now
    ) {
        int totalReviews = history.size();

        double averageRating = history.stream()
                .mapToInt(ReviewHistory::getRating)
                .average()
                .orElse(0.0);

        long failures = history.stream()
                .filter(review -> review.getRating() == 1)
                .count();

        double failureRate = totalReviews == 0
                ? 0.0
                : (double) failures / totalReviews;

        double daysSinceLastReview = state.getLastReviewedAt() == null
                ? 0.0
                : Duration.between(
                        state.getLastReviewedAt().toInstant(),
                        now.toInstant()
                ).toSeconds() / 86400.0;

        double daysUntilNextReview = Duration.between(
                now.toInstant(),
                state.getNextReviewAt().toInstant()
        ).toSeconds() / 86400.0;

        return new VocabularyLearningFeatures(
                state.getRepetitionCount(),
                state.getIntervalDays(),
                state.getEaseFactor() == null
                        ? 2.5
                        : state.getEaseFactor().doubleValue(),
                daysSinceLastReview,
                daysUntilNextReview,
                totalReviews,
                averageRating,
                failureRate
        );
    }
}
