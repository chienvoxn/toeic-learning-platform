
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.domain.ReviewState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Component
public class SrsScheduler {

    private static final BigDecimal MIN_EASE =
            new BigDecimal("1.30");

    private static final BigDecimal MAX_EASE =
            new BigDecimal("3.00");

    public ScheduleResult calculate(
            ReviewState state,
            int rating,
            OffsetDateTime reviewedAt
    ) {
        if (state == null || reviewedAt == null) {
            throw new IllegalArgumentException(
                "State and review time must not be null"
            );
        }

        if (rating < 1 || rating > 4) {
            throw new IllegalArgumentException(
                "Rating must be between 1 and 4"
            );
        }

        int repetition = state.getRepetitionCount();
        int previousInterval = state.getIntervalDays();

        BigDecimal ease = state.getEaseFactor();
        if (ease == null) {
            ease = new BigDecimal("2.50");
        }

        int newInterval;

        switch (rating) {
            case 1 -> {
                repetition = 0;
                newInterval = 0;
                ease = ease.subtract(new BigDecimal("0.20"));
            }
            case 2 -> {
                repetition = 0;
                newInterval = 1;
                ease = ease.subtract(new BigDecimal("0.15"));
            }
            case 3 -> {
                repetition++;
                newInterval = switch (repetition) {
                    case 1 -> 1;
                    case 2 -> 6;
                    default -> Math.max(
                        previousInterval + 1,
                        (int) Math.round(
                            previousInterval * ease.doubleValue()
                        )
                    );
                };
            }
            case 4 -> {
                repetition++;
                ease = ease.add(new BigDecimal("0.15"));
                newInterval = switch (repetition) {
                    case 1 -> 3;
                    case 2 -> 8;
                    default -> Math.max(
                        previousInterval + 1,
                        (int) Math.round(
                            previousInterval * ease.doubleValue() * 1.3
                        )
                    );
                };
            }
            default -> throw new IllegalStateException(
                "Unexpected rating"
            );
        }

        ease = ease.max(MIN_EASE)
                .min(MAX_EASE)
                .setScale(2, RoundingMode.HALF_UP);

        OffsetDateTime nextReviewAt =
                rating == 1
                    ? reviewedAt.plusMinutes(10)
                    : reviewedAt.plusDays(newInterval);

        return new ScheduleResult(
            repetition,
            previousInterval,
            newInterval,
            ease,
            nextReviewAt
        );
    }

    public record ScheduleResult(
            int repetitionCount,
            int previousInterval,
            int newInterval,
            BigDecimal easeFactor,
            OffsetDateTime nextReviewAt
    ) {
    }
}
