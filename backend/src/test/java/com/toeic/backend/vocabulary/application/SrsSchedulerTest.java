
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.domain.ReviewState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SrsSchedulerTest {

    private SrsScheduler scheduler;
    private OffsetDateTime now;
    private ReviewState state;

    @BeforeEach
    void setUp() {
        scheduler = new SrsScheduler();

        now = OffsetDateTime.of(
                2026, 11, 4, 10, 0, 0, 0,
                ZoneOffset.UTC
        );

        state = new ReviewState(
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }

    @Test
    void againShouldResetRepetitionAndReviewAfterTenMinutes() {
        state.updateSchedule(
                3, 15, new BigDecimal("2.50"),
                now.minusDays(15), now
        );

        var result = scheduler.calculate(state, 1, now);

        assertEquals(0, result.repetitionCount());
        assertEquals(15, result.previousInterval());
        assertEquals(0, result.newInterval());
        assertEquals(new BigDecimal("2.30"), result.easeFactor());
        assertEquals(now.plusMinutes(10), result.nextReviewAt());
    }

    @Test
    void hardShouldScheduleNextDay() {
        var result = scheduler.calculate(state, 2, now);

        assertEquals(0, result.repetitionCount());
        assertEquals(1, result.newInterval());
        assertEquals(new BigDecimal("2.35"), result.easeFactor());
        assertEquals(now.plusDays(1), result.nextReviewAt());
    }

    @Test
    void goodFirstReviewShouldScheduleOneDay() {
        var result = scheduler.calculate(state, 3, now);

        assertEquals(1, result.repetitionCount());
        assertEquals(1, result.newInterval());
        assertEquals(now.plusDays(1), result.nextReviewAt());
    }

    @Test
    void goodSecondReviewShouldScheduleSixDays() {
        state.updateSchedule(
                1, 1, new BigDecimal("2.50"),
                now.minusDays(1), now
        );

        var result = scheduler.calculate(state, 3, now);

        assertEquals(2, result.repetitionCount());
        assertEquals(6, result.newInterval());
        assertEquals(now.plusDays(6), result.nextReviewAt());
    }

    @Test
    void easyFirstReviewShouldScheduleThreeDays() {
        var result = scheduler.calculate(state, 4, now);

        assertEquals(1, result.repetitionCount());
        assertEquals(3, result.newInterval());
        assertEquals(new BigDecimal("2.65"), result.easeFactor());
        assertEquals(now.plusDays(3), result.nextReviewAt());
    }

    @Test
    void easySecondReviewShouldScheduleEightDays() {
        state.updateSchedule(
                1, 3, new BigDecimal("2.65"),
                now.minusDays(3), now
        );

        var result = scheduler.calculate(state, 4, now);

        assertEquals(2, result.repetitionCount());
        assertEquals(8, result.newInterval());
        assertEquals(new BigDecimal("2.80"), result.easeFactor());
    }

    @Test
    void shouldRejectInvalidRatings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> scheduler.calculate(state, 0, now)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> scheduler.calculate(state, 5, now)
        );
    }

    @Test
    void easeFactorShouldNotFallBelowMinimum() {
        state.updateSchedule(
                2, 6, new BigDecimal("1.30"),
                now.minusDays(6), now
        );

        var result = scheduler.calculate(state, 1, now);

        assertEquals(new BigDecimal("1.30"), result.easeFactor());
    }

    @Test
    void easeFactorShouldNotExceedMaximum() {
        state.updateSchedule(
                2, 8, new BigDecimal("3.00"),
                now.minusDays(8), now
        );

        var result = scheduler.calculate(state, 4, now);

        assertEquals(new BigDecimal("3.00"), result.easeFactor());
    }

    @Test
    void shouldRejectNullInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> scheduler.calculate(null, 3, now)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> scheduler.calculate(state, 3, null)
        );
    }
}
