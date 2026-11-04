package com.toeic.backend.vocabulary.api;

import java.time.LocalDate;

public record DailyLearningProgressResponse(
        LocalDate date,
        long reviewCount
) {
}