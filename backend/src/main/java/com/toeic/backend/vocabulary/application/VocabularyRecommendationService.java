
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.VocabularyRecommendationResponse;
import com.toeic.backend.vocabulary.domain.ReviewState;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.ReviewStateRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VocabularyRecommendationService {

    private final VocabularyRepository vocabularyRepository;
    private final ReviewStateRepository reviewStateRepository;

    public VocabularyRecommendationService(
            VocabularyRepository vocabularyRepository,
            ReviewStateRepository reviewStateRepository
    ) {
        this.vocabularyRepository = vocabularyRepository;
        this.reviewStateRepository = reviewStateRepository;
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

        List<VocabularyRecommendationResponse> recommendations =
                new ArrayList<>();

        // Priority 1: Previously reviewed vocabularies due for review
        List<ReviewState> dueStates =
                reviewStateRepository
                        .findByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                                userId,
                                now,
                                PageRequest.of(0, limit)
                        );

        if (!dueStates.isEmpty()) {
            List<UUID> vocabularyIds = dueStates.stream()
                    .map(ReviewState::getVocabularyId)
                    .toList();

            List<Vocabulary> vocabularies =
                    vocabularyRepository.findByCreatedByAndVocabularyIdIn(
                            userId,
                            vocabularyIds
                    );

            Map<UUID, Vocabulary> vocabularyMap = new HashMap<>();

            for (Vocabulary vocabulary : vocabularies) {
                vocabularyMap.put(
                        vocabulary.getVocabularyId(),
                        vocabulary
                );
            }

            // Preserve ordering by nextReviewAt
            for (ReviewState state : dueStates) {
                Vocabulary vocabulary =
                        vocabularyMap.get(state.getVocabularyId());

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

        // Priority 2: Vocabularies never reviewed
        int remaining = limit - recommendations.size();

        if (remaining > 0) {
            List<Vocabulary> unreviewed =
                    vocabularyRepository.findUnreviewedVocabularies(
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
}
