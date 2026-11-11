
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.LearningOverviewResponse;
import com.toeic.backend.vocabulary.infrastructure.ReviewHistoryRepository;
import com.toeic.backend.vocabulary.infrastructure.ReviewStateRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class LearningProfileService {

    private static final int MASTERY_THRESHOLD = 5;

    private final VocabularyRepository vocabularyRepository;
    private final ReviewStateRepository reviewStateRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;

    public LearningProfileService(
            VocabularyRepository vocabularyRepository,
            ReviewStateRepository reviewStateRepository,
            ReviewHistoryRepository reviewHistoryRepository
    ) {
        this.vocabularyRepository = vocabularyRepository;
        this.reviewStateRepository = reviewStateRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
    }

    @Transactional(readOnly = true)
    public LearningOverviewResponse getOverview(UUID userId) {

        OffsetDateTime now =
                OffsetDateTime.now(ZoneOffset.UTC);

        long total = vocabularyRepository
                .countByCreatedBy(userId);

        long reviewed = reviewStateRepository
                .countByUserIdAndLastReviewedAtIsNotNull(userId);

        long unreviewed = Math.max(0, total - reviewed);

        long due = reviewStateRepository
                .countByUserIdAndNextReviewAtLessThanEqual(
                        userId,
                        now
                );

        long mastered = reviewStateRepository
                .countByUserIdAndLastReviewedAtIsNotNullAndRepetitionCountGreaterThanEqual(
                        userId,
                        MASTERY_THRESHOLD
                );

        long totalReviews = reviewHistoryRepository
                .countReviewsByUserId(userId);

        return new LearningOverviewResponse(
                total,
                reviewed,
                unreviewed,
                due,
                mastered,
                totalReviews
        );
    }
}
