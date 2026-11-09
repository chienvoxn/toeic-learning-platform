
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.VocabularyReviewService;
import com.toeic.backend.vocabulary.domain.ReviewHistory;
import com.toeic.backend.vocabulary.domain.ReviewState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyReviewControllerTest {

    @Mock
    private VocabularyReviewService reviewService;

    @InjectMocks
    private VocabularyReviewController controller;

    private UUID userId;
    private UUID vocabularyId;
    private SessionPrincipal principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        vocabularyId = UUID.randomUUID();

        principal = mock(SessionPrincipal.class);
        lenient().when(principal.userId()).thenReturn(userId);
    }

    @Test
    void submitReviewShouldReturnOk() {
        ReviewState state = new ReviewState(userId, vocabularyId);

        when(reviewService.submitReview(userId, vocabularyId, 3))
                .thenReturn(state);

        var response = controller.submitReview(
                principal,
                vocabularyId,
                new SubmitReviewRequest(3)
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                vocabularyId,
                response.getBody().vocabularyId()
        );

        verify(reviewService).submitReview(
                userId, vocabularyId, 3
        );
    }

    @Test
    void getDueReviewsShouldReturnList() {
        ReviewState state = new ReviewState(userId, vocabularyId);

        when(reviewService.getDueReviews(userId))
                .thenReturn(List.of(state));

        var result = controller.getDueReviews(principal);

        assertEquals(1, result.size());
        assertEquals(vocabularyId, result.get(0).vocabularyId());
    }

    @Test
    void getStatisticsShouldReturnCounts() {
        when(reviewService.getStatistics(userId))
                .thenReturn(
                        new VocabularyReviewService.ReviewStatistics(
                                10, 3
                        )
                );

        var result = controller.getStatistics(principal);

        assertEquals(10, result.totalVocabulary());
        assertEquals(3, result.dueVocabulary());
    }

    @Test
    void getReviewStateShouldReturnState() {
        ReviewState state = new ReviewState(userId, vocabularyId);

        when(reviewService.getReviewState(userId, vocabularyId))
                .thenReturn(state);

        var result = controller.getReviewState(
                principal, vocabularyId
        );

        assertEquals(vocabularyId, result.vocabularyId());
        assertEquals(0, result.repetitionCount());
    }

    @Test
    void getReviewHistoryShouldReturnHistory() {
        ReviewHistory history = new ReviewHistory(
                UUID.randomUUID(), 3, 0, 1
        );

        when(reviewService.getReviewHistory(userId, vocabularyId))
                .thenReturn(List.of(history));

        var result = controller.getReviewHistory(
                principal, vocabularyId
        );

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).rating());
    }
}
