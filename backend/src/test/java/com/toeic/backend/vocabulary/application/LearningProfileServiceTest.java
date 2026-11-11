
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.LearningOverviewResponse;
import com.toeic.backend.vocabulary.infrastructure.ReviewHistoryRepository;
import com.toeic.backend.vocabulary.infrastructure.ReviewStateRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningProfileServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private ReviewStateRepository reviewStateRepository;

    @Mock
    private ReviewHistoryRepository reviewHistoryRepository;

    private LearningProfileService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new LearningProfileService(
                vocabularyRepository,
                reviewStateRepository,
                reviewHistoryRepository
        );
        userId = UUID.randomUUID();
    }

    @Test
    void shouldReturnLearningOverview() {
        when(vocabularyRepository.countByCreatedBy(userId))
                .thenReturn(120L);

        when(reviewStateRepository
                .countByUserIdAndLastReviewedAtIsNotNull(userId))
                .thenReturn(80L);

        when(reviewStateRepository
                .countByUserIdAndNextReviewAtLessThanEqual(
                        eq(userId), any(OffsetDateTime.class)))
                .thenReturn(15L);

        when(reviewStateRepository
                .countByUserIdAndLastReviewedAtIsNotNullAndRepetitionCountGreaterThanEqual(
                        userId, 5))
                .thenReturn(35L);

        when(reviewHistoryRepository.countReviewsByUserId(userId))
                .thenReturn(250L);

        LearningOverviewResponse result =
                service.getOverview(userId);

        assertAll(
                () -> assertEquals(120, result.totalVocabularies()),
                () -> assertEquals(80, result.reviewedVocabularies()),
                () -> assertEquals(40, result.unreviewedVocabularies()),
                () -> assertEquals(15, result.dueForReview()),
                () -> assertEquals(35, result.masteredVocabularies()),
                () -> assertEquals(250, result.totalReviews())
        );
    }

    @Test
    void shouldReturnZerosForNewUser() {
        LearningOverviewResponse result =
                service.getOverview(userId);

        assertAll(
                () -> assertEquals(0, result.totalVocabularies()),
                () -> assertEquals(0, result.reviewedVocabularies()),
                () -> assertEquals(0, result.unreviewedVocabularies()),
                () -> assertEquals(0, result.dueForReview()),
                () -> assertEquals(0, result.masteredVocabularies()),
                () -> assertEquals(0, result.totalReviews())
        );
    }

    @Test
    void shouldNeverReturnNegativeUnreviewedCount() {
        when(vocabularyRepository.countByCreatedBy(userId))
                .thenReturn(5L);

        when(reviewStateRepository
                .countByUserIdAndLastReviewedAtIsNotNull(userId))
                .thenReturn(10L);

        LearningOverviewResponse result =
                service.getOverview(userId);

        assertEquals(0, result.unreviewedVocabularies());
    }
}
