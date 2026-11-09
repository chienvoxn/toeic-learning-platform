
package com.toeic.backend.vocabulary.application;

public record VocabularyLearningFeatures(
        int repetitionCount,
        int intervalDays,
        double easeFactor,
        double daysSinceLastReview,
        double daysUntilNextReview,
        int totalReviews,
        double averageRating,
        double failureRate
) {
}
