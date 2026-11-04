package com.toeic.backend.vocabulary.api;

import java.util.List;

public record LearningProgressResponse(
        int days,
        List<DailyLearningProgressResponse> dailyProgress
) {
}