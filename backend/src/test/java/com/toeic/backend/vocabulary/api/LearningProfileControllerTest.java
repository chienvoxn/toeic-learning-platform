package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.LearningProfileService;
import com.toeic.backend.vocabulary.application.LearningStatisticsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningProfileControllerTest {

    @Mock
    private LearningProfileService learningProfileService;

    @Mock
    private LearningStatisticsService learningStatisticsService;

    @InjectMocks
    private LearningProfileController controller;
    private UUID userId;
    private SessionPrincipal principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        principal = mock(SessionPrincipal.class);
    }

    @Test
    void getOverviewShouldReturnLearningStatistics() {
        when(principal.userId()).thenReturn(userId);

        LearningOverviewResponse expected =
                new LearningOverviewResponse(
                        120,
                        80,
                        40,
                        15,
                        35,
                        250
                );

        when(learningProfileService.getOverview(userId))
                .thenReturn(expected);

        LearningOverviewResponse result =
                controller.getOverview(principal);

        assertNotNull(result);
        assertEquals(120, result.totalVocabularies());
        assertEquals(80, result.reviewedVocabularies());
        assertEquals(40, result.unreviewedVocabularies());
        assertEquals(15, result.dueForReview());
        assertEquals(35, result.masteredVocabularies());
        assertEquals(250, result.totalReviews());

        verify(learningProfileService).getOverview(userId);
        verifyNoMoreInteractions(learningProfileService);
    }

    @Test
    void getOverviewShouldReturnZeroForNewUser() {
        when(principal.userId()).thenReturn(userId);

        LearningOverviewResponse expected =
                new LearningOverviewResponse(
                        0, 0, 0, 0, 0, 0
                );

        when(learningProfileService.getOverview(userId))
                .thenReturn(expected);

        LearningOverviewResponse result =
                controller.getOverview(principal);

        assertNotNull(result);
        assertEquals(0, result.totalVocabularies());
        assertEquals(0, result.reviewedVocabularies());
        assertEquals(0, result.totalReviews());

        verify(learningProfileService).getOverview(userId);
    }

    @Test
    void getOverviewShouldUseAuthenticatedUserId() {
        when(principal.userId()).thenReturn(userId);
        UUID anotherUserId = UUID.randomUUID();

        LearningOverviewResponse expected =
                new LearningOverviewResponse(
                        10, 5, 5, 2, 1, 20
                );

        when(learningProfileService.getOverview(userId))
                .thenReturn(expected);

        controller.getOverview(principal);

        verify(learningProfileService).getOverview(userId);
        verify(learningProfileService, never())
                .getOverview(anotherUserId);
    }

    @Test
    void getStreakShouldUseAuthenticatedUserAndReturnStreak() {
        when(principal.userId()).thenReturn(userId);

        LearningStreakResponse expected = new LearningStreakResponse(30, 4, 7);
        when(learningStatisticsService.getStreak(userId, 30)).thenReturn(expected);

        assertEquals(expected, controller.getStreak(principal, 30));
        verify(learningStatisticsService).getStreak(userId, 30);
    }

    @Test
    void getStreakShouldRejectInvalidDayWindow() {
        ResponseStatusException error =
                assertThrows(ResponseStatusException.class,
                        () -> controller.getStreak(principal, 91));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(learningStatisticsService);
    }
}
