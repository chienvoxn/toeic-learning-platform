
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.VocabularyRecommendationResponse;
import com.toeic.backend.vocabulary.domain.ReviewHistory;
import com.toeic.backend.vocabulary.domain.ReviewState;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.ReviewHistoryRepository;
import com.toeic.backend.vocabulary.infrastructure.ReviewStateRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VocabularyRecommendationService {

    private static final int MAX_ML_CANDIDATES = 500;

    private final VocabularyRepository vocabularyRepository;
    private final ReviewStateRepository reviewStateRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final VocabularyFeatureExtractor featureExtractor;
    private final VocabularyMlInferenceService mlInferenceService;

    public VocabularyRecommendationService(
            VocabularyRepository vocabularyRepository,
            ReviewStateRepository reviewStateRepository,
            ReviewHistoryRepository reviewHistoryRepository,
            VocabularyFeatureExtractor featureExtractor,
            VocabularyMlInferenceService mlInferenceService
    ) {
        this.vocabularyRepository = vocabularyRepository;
        this.reviewStateRepository = reviewStateRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.featureExtractor = featureExtractor;
        this.mlInferenceService = mlInferenceService;
    }

    @Transactional(readOnly = true)
    public List<VocabularyRecommendationResponse> getRecommendations(
            UUID userId,
            int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and 100"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        boolean mlAvailable =
                mlInferenceService.isModelAvailable();

        int candidateLimit = mlAvailable
                ? MAX_ML_CANDIDATES
                : limit;

        // Priority 1: Previously reviewed vocabularies due for review.
        List<ReviewState> dueStates =
                reviewStateRepository
                        .findByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                                userId,
                                now,
                                PageRequest.of(0, candidateLimit)
                        );

        // Rank by ML only when a valid model is available.
        // Otherwise preserve the original SRS ordering.
        if (mlAvailable && !dueStates.isEmpty()) {
            dueStates = rankDueStates(dueStates, now);
        }

        List<VocabularyRecommendationResponse> recommendations =
                new ArrayList<>();

        if (!dueStates.isEmpty()) {
            List<UUID> vocabularyIds = dueStates.stream()
                    .map(ReviewState::getVocabularyId)
                    .toList();

            List<Vocabulary> vocabularies =
                    vocabularyRepository
                            .findByCreatedByAndVocabularyIdIn(
                                    userId,
                                    vocabularyIds
                            );

            Map<UUID, Vocabulary> vocabularyMap =
                    new HashMap<>();

            for (Vocabulary vocabulary : vocabularies) {
                vocabularyMap.put(
                        vocabulary.getVocabularyId(),
                        vocabulary
                );
            }

            for (ReviewState state : dueStates) {
                if (recommendations.size() >= limit) {
                    break;
                }

                Vocabulary vocabulary =
                        vocabularyMap.get(
                                state.getVocabularyId()
                        );

                if (vocabulary != null) {
                    recommendations.add(
                            VocabularyRecommendationResponse.due(
                                    vocabulary,
                                    state.getNextReviewAt()
                            )
                    );
                }
            }
        }

        // Priority 2: Vocabularies never reviewed.
        int remaining = limit - recommendations.size();

        if (remaining > 0) {
            List<Vocabulary> unreviewed =
                    vocabularyRepository
                            .findUnreviewedVocabularies(
                                    userId,
                                    PageRequest.of(0, remaining)
                            );

            for (Vocabulary vocabulary : unreviewed) {
                recommendations.add(
                        VocabularyRecommendationResponse.newVocabulary(
                                vocabulary
                        )
                );
            }
        }

        return recommendations;
    }

    private List<ReviewState> rankDueStates(
            List<ReviewState> dueStates,
            OffsetDateTime now
    ) {
        List<ScoredReviewState> scored =
                new ArrayList<>();

        for (ReviewState state : dueStates) {
            List<ReviewHistory> history =
                    reviewHistoryRepository
                            .findByReviewStateIdOrderByReviewedAtDesc(
                                    state.getReviewStateId()
                            );

            VocabularyLearningFeatures features =
                    featureExtractor.extract(
                            state,
                            history,
                            now
                    );

            Optional<Double> prediction =
                    mlInferenceService
                            .predictForgetProbability(features);

            // If any prediction fails, use the original
            // nextReviewAt ordering for the entire list.
            if (prediction.isEmpty()) {
                return dueStates;
            }

            double probability = prediction.get();

            if (!Double.isFinite(probability)
                    || probability < 0.0
                    || probability > 1.0) {
                return dueStates;
            }

            scored.add(
                    new ScoredReviewState(
                            state,
                            probability
                    )
            );
        }

        scored.sort(
                Comparator.comparingDouble(
                        ScoredReviewState::probability
                ).reversed()
                .thenComparing(
                        scoredState ->
                                scoredState.state().getNextReviewAt()
                )
                .thenComparing(
                        scoredState ->
                                scoredState.state().getVocabularyId()
                )
        );

        return scored.stream()
                .map(ScoredReviewState::state)
                .toList();
    }

    private record ScoredReviewState(
            ReviewState state,
            double probability
    ) {
    }
}
