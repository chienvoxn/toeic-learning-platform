
package com.toeic.backend.vocabulary.api;

public record LearningStatisticsResponse(
        long totalReviews,
        long againCount,
        long hardCount,
        long goodCount,
        long easyCount
) {
}
