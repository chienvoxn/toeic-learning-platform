
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.domain.ReviewHistory;
import com.toeic.backend.vocabulary.domain.ReviewState;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class VocabularyTrainingDatasetBuilder {

    private final SrsScheduler scheduler;
    private final VocabularyFeatureExtractor featureExtractor;

    public VocabularyTrainingDatasetBuilder(
            SrsScheduler scheduler,
            VocabularyFeatureExtractor featureExtractor
    ) {
        this.scheduler = scheduler;
        this.featureExtractor = featureExtractor;
    }

    public List<VocabularyTrainingExample> build(
            UUID userId,
            UUID vocabularyId,
            List<ReviewHistory> history
    ) {
        if (userId == null || vocabularyId == null || history == null) {
            throw new IllegalArgumentException(
                    "User ID, vocabulary ID and history must not be null"
            );
        }

        List<ReviewHistory> ordered = history.stream()
                .sorted(
                        Comparator.comparing(
                                ReviewHistory::getReviewedAt
                        ).thenComparing(
                                ReviewHistory::getReviewHistoryId
                        )
                )
                .toList();

        if (ordered.size() < 2) {
            return List.of();
        }

        ReviewState state = new ReviewState(userId, vocabularyId);

        List<ReviewHistory> previousReviews = new ArrayList<>();
        List<VocabularyTrainingExample> examples = new ArrayList<>();

        for (int index = 0; index < ordered.size(); index++) {
            ReviewHistory review = ordered.get(index);
            OffsetDateTime reviewedAt = review.getReviewedAt();

            // Reconstruct the schedule without mutating the state.
            SrsScheduler.ScheduleResult result =
                    scheduler.calculate(
                            state,
                            review.getRating(),
                            reviewedAt
                    );

            // Reject incomplete or inconsistent histories.
            if (review.getPreviousInterval() == null
                    || review.getNewInterval() == null
                    || review.getPreviousInterval()
                            != result.previousInterval()
                    || review.getNewInterval()
                            != result.newInterval()) {
                return List.of();
            }

            // Generate features before applying the current rating.
            if (index > 0) {
                VocabularyLearningFeatures features =
                        featureExtractor.extract(
                                state,
                                previousReviews,
                                reviewedAt
                        );

                int label = review.getRating() == 1 ? 1 : 0;

                examples.add(
                        new VocabularyTrainingExample(
                                vocabularyId,
                                reviewedAt,
                                features,
                                label
                        )
                );
            }

            state.updateSchedule(
                    result.repetitionCount(),
                    result.newInterval(),
                    result.easeFactor(),
                    reviewedAt,
                    result.nextReviewAt()
            );

            previousReviews.add(review);
        }

        return examples;
    }
}
