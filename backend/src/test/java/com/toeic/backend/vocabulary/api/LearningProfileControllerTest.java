
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.LearningProfileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningProfileControllerTest {

    @Mock
    private LearningProfileService learningProfileService;

    @InjectMocks
    private LearningProfileController controller;

    private UUID userId;
    private SessionPrincipal principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        principal = mock(SessionPrincipal.class);
        when(principal.userId()).thenReturn(userId);
    }

    @Test
    void getOverviewShouldReturnLearningStatistics() {
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
}
