package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.LearningStreakResponse;
import com.toeic.backend.vocabulary.domain.ReviewHistory;
import com.toeic.backend.vocabulary.infrastructure.ReviewHistoryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningStatisticsServiceTest {

    private final ReviewHistoryRepository repository = mock(ReviewHistoryRepository.class);
    private final LearningStatisticsService service = new LearningStatisticsService(repository);
    private final UUID userId = UUID.randomUUID();

    private ReviewHistory reviewDaysAgo(int daysAgo) {
        ReviewHistory history = mock(ReviewHistory.class);
        LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo);
        when(history.getReviewedAt()).thenReturn(date.atTime(12, 0).atOffset(ZoneOffset.UTC));
        return history;
    }

    private void withReviews(ReviewHistory... reviews) {
        when(repository.findReviewsByUserIdAndPeriod(eq(userId), any(OffsetDateTime.class),
                any(OffsetDateTime.class))).thenReturn(List.of(reviews));
    }

    @Test
    void streakCountsTodayAndYesterdayWithoutDoubleCountingSameDay() {
        withReviews(reviewDaysAgo(0), reviewDaysAgo(0), reviewDaysAgo(1), reviewDaysAgo(3));
        LearningStreakResponse result = service.getStreak(userId, 7);
        assertEquals(2, result.currentStreak());
        assertEquals(2, result.longestStreak());
    }

    @Test
    void yesterdayKeepsCurrentStreakAliveUntilTodayEnds() {
        withReviews(reviewDaysAgo(1), reviewDaysAgo(2), reviewDaysAgo(4));
        LearningStreakResponse result = service.getStreak(userId, 7);
        assertEquals(2, result.currentStreak());
        assertEquals(2, result.longestStreak());
    }

    @Test
    void streakResetsAfterTwoMissedDays() {
        withReviews(reviewDaysAgo(2), reviewDaysAgo(3));
        LearningStreakResponse result = service.getStreak(userId, 7);
        assertEquals(0, result.currentStreak());
        assertEquals(2, result.longestStreak());
    }

    @Test
    void emptyHistoryReturnsZeroStreaks() {
        withReviews();
        LearningStreakResponse result = service.getStreak(userId, 30);
        assertEquals(0, result.currentStreak());
        assertEquals(0, result.longestStreak());
    }

    @Test
    void daysOutsideAllowedRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.getStreak(userId, 0));
        assertThrows(IllegalArgumentException.class, () -> service.getStreak(userId, 91));
    }
}
