package com.toeic.backend.assessment.domain;

import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeAttemptTest {
    private final OffsetDateTime time = OffsetDateTime.parse("2026-11-10T00:00:00Z");
    @Test void pinsAndOrderAreImmutableWithoutCreatingTestVersionOrAnswers() {
        var group = UUID.randomUUID();
        var pins = new ArrayList<>(List.of(new PracticeAttempt.Pin(UUID.randomUUID(), group),
                new PracticeAttempt.Pin(UUID.randomUUID(), group), new PracticeAttempt.Pin(UUID.randomUUID(), null)));
        var actor = UUID.randomUUID();
        var attempt = new PracticeAttempt(actor, time, time.plusHours(1), pins);
        pins.clear();
        assertEquals(actor, attempt.getUserId());
        assertNull(attempt.getTestVersionId());
        assertEquals("PRACTICE_SNAPSHOT", attempt.getSourceType());
        assertEquals("IN_PROGRESS", attempt.getStatus());
        assertEquals(3, attempt.getTotalQuestions());
        assertEquals(List.of(1, 2, 3), attempt.getQuestions().stream().map(PracticeQuestionSnapshot::getQuestionOrder).toList());
        assertEquals(group, attempt.getQuestions().getFirst().getGroupVersionId());
        assertThrows(UnsupportedOperationException.class, () -> attempt.getQuestions().clear());
    }
    @Test void invalidPinsExpiryAndActorAreRejectedBeforePersistence() {
        var pin = new PracticeAttempt.Pin(UUID.randomUUID(), null);
        assertThrows(IllegalArgumentException.class, () -> new PracticeAttempt(UUID.randomUUID(), time, time, List.of(pin)));
        assertThrows(IllegalArgumentException.class, () -> new PracticeAttempt(UUID.randomUUID(), time, time.plusDays(1), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PracticeAttempt(UUID.randomUUID(), time, time.plusDays(1), List.of(pin, pin)));
        assertThrows(NullPointerException.class, () -> new PracticeAttempt(null, time, time.plusDays(1), List.of(pin)));
        assertThrows(NullPointerException.class, () -> new PracticeAttempt.Pin(null, null));
    }
}
