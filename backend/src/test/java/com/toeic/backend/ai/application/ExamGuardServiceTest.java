package com.toeic.backend.ai.application;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExamGuardServiceTest {

    private JdbcTemplate jdbc;
    private ExamGuardService guard;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        guard = new ExamGuardService(jdbc);
        userId = UUID.randomUUID();
    }

    @Test
    void activeExamShouldBeDetected() {
        when(jdbc.queryForObject(
            anyString(), eq(Boolean.class), eq(userId)
        )).thenReturn(true);

        assertTrue(guard.hasActiveExam(userId));
    }

    @Test
    void noActiveExamShouldAllowAi() {
        when(jdbc.queryForObject(
            anyString(), eq(Boolean.class), eq(userId)
        )).thenReturn(false);

        assertDoesNotThrow(
            () -> guard.requireAiAllowed(userId)
        );
    }

    @Test
    void activeExamShouldRejectAi() {
        when(jdbc.queryForObject(
            anyString(), eq(Boolean.class), eq(userId)
        )).thenReturn(true);

        ChatFailure failure = assertThrows(
            ChatFailure.class,
            () -> guard.requireAiAllowed(userId)
        );

        assertEquals(403, failure.status);
        assertEquals("AI_EXAM_RESTRICTED", failure.code);
    }

    @Test
    void databaseFailureShouldNotAllowAi() {
        when(jdbc.queryForObject(
            anyString(), eq(Boolean.class), eq(userId)
        )).thenThrow(new IllegalStateException("DB unavailable"));

        assertThrows(
            IllegalStateException.class,
            () -> guard.requireAiAllowed(userId)
        );
    }
}
