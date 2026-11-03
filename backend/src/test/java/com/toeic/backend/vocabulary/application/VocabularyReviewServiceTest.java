
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.domain.ReviewHistory;
import com.toeic.backend.vocabulary.domain.ReviewState;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.ReviewHistoryRepository;
import com.toeic.backend.vocabulary.infrastructure.ReviewStateRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyReviewServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private ReviewStateRepository reviewStateRepository;

    @Mock
    private ReviewHistoryRepository reviewHistoryRepository;

    @Mock
    private SrsScheduler scheduler;

    @InjectMocks
    private VocabularyReviewService service;

    private UUID userId;
    private UUID vocabularyId;
    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        vocabulary = new Vocabulary(
                userId,
                "accomplish",
                "hoan thanh"
        );

        vocabularyId = vocabulary.getVocabularyId();
    }

    private void mockOwnedVocabularyForRead() {
        when(vocabularyRepository.findById(vocabularyId))
                .thenReturn(Optional.of(vocabulary));
    }

    private void mockOwnedVocabularyForUpdate() {
        when(vocabularyRepository.findOwnedByIdForUpdate(
                vocabularyId,
                userId
        )).thenReturn(Optional.of(vocabulary));
    }

    @Test
    void submitReviewShouldCreateStateAndHistory() {
        mockOwnedVocabularyForUpdate();

        when(reviewStateRepository.findByUserIdAndVocabularyId(
                userId,
                vocabularyId
        )).thenReturn(Optional.empty());

        when(scheduler.calculate(any(), eq(3), any()))
                .thenAnswer(invocation -> {
                    OffsetDateTime now =
                            invocation.getArgument(2);

                    return new SrsScheduler.ScheduleResult(
                            1,
                            0,
                            1,
                            new BigDecimal("2.50"),
                            now.plusDays(1)
                    );
                });

        when(reviewStateRepository.save(any(ReviewState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewState result =
                service.submitReview(userId, vocabularyId, 3);

        assertEquals(1, result.getRepetitionCount());
        assertEquals(1, result.getIntervalDays());

        verify(vocabularyRepository).findOwnedByIdForUpdate(
                vocabularyId,
                userId
        );

        ArgumentCaptor<ReviewHistory> captor =
                ArgumentCaptor.forClass(ReviewHistory.class);

        verify(reviewHistoryRepository).save(captor.capture());

        ReviewHistory history = captor.getValue();

        assertEquals(
                result.getReviewStateId(),
                history.getReviewStateId()
        );
        assertEquals(3, history.getRating());
        assertEquals(0, history.getPreviousInterval());
        assertEquals(1, history.getNewInterval());
    }

    @Test
    void submitReviewShouldRejectInvalidRating() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submitReview(
                        userId,
                        vocabularyId,
                        5
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                vocabularyRepository,
                reviewStateRepository,
                reviewHistoryRepository,
                scheduler
        );
    }

    @Test
    void submitReviewShouldRejectForeignVocabulary() {
        when(vocabularyRepository.findOwnedByIdForUpdate(
                vocabularyId,
                userId
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submitReview(
                        userId,
                        vocabularyId,
                        3
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(vocabularyRepository).findOwnedByIdForUpdate(
                vocabularyId,
                userId
        );

        verifyNoInteractions(
                reviewStateRepository,
                reviewHistoryRepository,
                scheduler
        );
    }

    @Test
    void getReviewStateShouldReturnExistingState() {
        mockOwnedVocabularyForRead();

        ReviewState state = new ReviewState(
                userId,
                vocabularyId
        );

        when(reviewStateRepository.findByUserIdAndVocabularyId(
                userId,
                vocabularyId
        )).thenReturn(Optional.of(state));

        ReviewState result =
                service.getReviewState(userId, vocabularyId);

        assertSame(state, result);
    }

    @Test
    void getReviewStateShouldReturn404WhenMissing() {
        mockOwnedVocabularyForRead();

        when(reviewStateRepository.findByUserIdAndVocabularyId(
                userId,
                vocabularyId
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getReviewState(
                        userId,
                        vocabularyId
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void getDueReviewsShouldReturnUserDueStates() {
        ReviewState state = new ReviewState(
                userId,
                vocabularyId
        );

        when(reviewStateRepository
                .findByUserIdAndNextReviewAtLessThanEqual(
                        eq(userId),
                        any()
                ))
                .thenReturn(List.of(state));

        List<ReviewState> result =
                service.getDueReviews(userId);

        assertEquals(1, result.size());
        assertSame(state, result.get(0));
    }

    @Test
    void getStatisticsShouldReturnCounts() {
        when(reviewStateRepository.countByUserId(userId))
                .thenReturn(12L);

        when(reviewStateRepository
                .countByUserIdAndNextReviewAtLessThanEqual(
                        eq(userId),
                        any()
                ))
                .thenReturn(4L);

        var result = service.getStatistics(userId);

        assertEquals(12, result.totalVocabulary());
        assertEquals(4, result.dueVocabulary());
    }

    @Test
    void getReviewHistoryShouldReturnHistory() {
        mockOwnedVocabularyForRead();

        ReviewState state = new ReviewState(
                userId,
                vocabularyId
        );

        when(reviewStateRepository.findByUserIdAndVocabularyId(
                userId,
                vocabularyId
        )).thenReturn(Optional.of(state));

        ReviewHistory history = new ReviewHistory(
                state.getReviewStateId(),
                3,
                0,
                1
        );

        when(reviewHistoryRepository
                .findByReviewStateIdOrderByReviewedAtDesc(
                        state.getReviewStateId()
                ))
                .thenReturn(List.of(history));

        List<ReviewHistory> result =
                service.getReviewHistory(
                        userId,
                        vocabularyId
                );

        assertEquals(1, result.size());
        assertSame(history, result.get(0));
    }
}
