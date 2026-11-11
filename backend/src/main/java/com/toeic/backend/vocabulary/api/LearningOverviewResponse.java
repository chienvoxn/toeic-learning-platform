
package com.toeic.backend.vocabulary.api;

public record LearningOverviewResponse(
        long totalVocabularies,
        long reviewedVocabularies,
        long unreviewedVocabularies,
        long dueForReview,
        long masteredVocabularies,
        long totalReviews
) {
}
