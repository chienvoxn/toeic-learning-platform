package com.toeic.backend.vocabulary.api;

/** Streaks are computed in UTC within the requested rolling window. */
public record LearningStreakResponse(
        int days,
        int currentStreak,
        int longestStreak
) {
}
