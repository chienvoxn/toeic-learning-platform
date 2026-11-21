package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.domain.ReviewState;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.ReviewHistoryRepository;
import com.toeic.backend.vocabulary.infrastructure.ReviewStateRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VocabularyRecommendationServiceTest {
    @Test
    void ranksDueCardsByModelAndFallsBackToDueOrder() {
        UUID userId = UUID.randomUUID();
        var earlier = new Vocabulary(userId, "earlier", "first");
        var later = new Vocabulary(userId, "later", "second");
        var earlierState = new ReviewState(userId, earlier.getVocabularyId());
        var laterState = new ReviewState(userId, later.getVocabularyId());
        var vocabularies = mock(VocabularyRepository.class);
        var states = mock(ReviewStateRepository.class);
        var histories = mock(ReviewHistoryRepository.class);
        var extractor = mock(VocabularyFeatureExtractor.class);
        var inference = mock(VocabularyMlInferenceService.class);
        var earlierFeatures = new VocabularyLearningFeatures(1, 1, 2.5, 1, 0, 1, 3, 0);
        var laterFeatures = new VocabularyLearningFeatures(2, 1, 2.5, 1, 0, 1, 3, 0);

        when(states.findByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                eq(userId), any(OffsetDateTime.class), any())).thenReturn(List.of(earlierState, laterState));
        when(vocabularies.findByCreatedByAndVocabularyIdIn(eq(userId), anyList())).thenReturn(List.of(earlier, later));
        when(inference.isModelAvailable()).thenReturn(true);
        when(extractor.extract(eq(earlierState), anyList(), any())).thenReturn(earlierFeatures);
        when(extractor.extract(eq(laterState), anyList(), any())).thenReturn(laterFeatures);
        when(inference.predictForgetProbability(earlierFeatures)).thenReturn(Optional.of(0.2));
        when(inference.predictForgetProbability(laterFeatures)).thenReturn(Optional.of(0.9));
        var service = new VocabularyRecommendationService(vocabularies, states, histories, extractor, inference);

        assertEquals(later.getVocabularyId(), service.getRecommendations(userId, 2).getFirst().vocabulary().vocabularyId());
        when(inference.predictForgetProbability(laterFeatures)).thenReturn(Optional.empty());
        assertEquals(earlier.getVocabularyId(), service.getRecommendations(userId, 2).getFirst().vocabulary().vocabularyId());
    }
}
