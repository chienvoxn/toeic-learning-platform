
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.DailyLearningProgressResponse;
import com.toeic.backend.vocabulary.api.LearningProgressResponse;
import com.toeic.backend.vocabulary.api.LearningStatisticsResponse;
import com.toeic.backend.vocabulary.domain.ReviewHistory;
import com.toeic.backend.vocabulary.infrastructure.ReviewHistoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LearningStatisticsService {

    private final ReviewHistoryRepository reviewHistoryRepository;

    public LearningStatisticsService(
            ReviewHistoryRepository reviewHistoryRepository
    ) {
        this.reviewHistoryRepository = reviewHistoryRepository;
    }

    @Transactional(readOnly = true)
    public LearningStatisticsResponse getStatistics(UUID userId) {

        List<Object[]> results =
                reviewHistoryRepository.countReviewsGroupedByRating(userId);

        long[] counts = new long[5];

        for (Object[] row : results) {
            int rating = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();

            if (rating >= 1 && rating <= 4) {
                counts[rating] = count;
            }
        }

        long totalReviews = counts[1] + counts[2]
                + counts[3] + counts[4];

        return new LearningStatisticsResponse(
                totalReviews,
                counts[1],
                counts[2],
                counts[3],
                counts[4]
        );
    }

    @Transactional(readOnly = true)
    public LearningProgressResponse getProgress(
            UUID userId,
            int days
    ) {
        if (days < 1 || days > 90) {
            throw new IllegalArgumentException(
                    "Days must be between 1 and 90"
            );
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = today.minusDays(days - 1L);

        OffsetDateTime from =
                startDate.atStartOfDay().atOffset(ZoneOffset.UTC);

        OffsetDateTime to =
                today.plusDays(1).atStartOfDay()
                        .atOffset(ZoneOffset.UTC);

        List<ReviewHistory> histories =
                reviewHistoryRepository.findReviewsByUserIdAndPeriod(
                        userId,
                        from,
                        to
                );

        Map<LocalDate, Long> counts = new HashMap<>();

        for (ReviewHistory history : histories) {
            LocalDate date = history.getReviewedAt()
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDate();

            counts.merge(date, 1L, Long::sum);
        }

        List<DailyLearningProgressResponse> progress =
                new ArrayList<>();

        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);

            progress.add(new DailyLearningProgressResponse(
                    date,
                    counts.getOrDefault(date, 0L)
            ));
        }

        return new LearningProgressResponse(days, progress);
    }
}
