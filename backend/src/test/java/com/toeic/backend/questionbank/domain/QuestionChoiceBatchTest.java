package com.toeic.backend.questionbank.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class QuestionChoiceBatchTest {
    private QuestionVersion draft() {
        var question = new Question(new ToeicPart(1, "LISTENING", "Fixture"), null,
                new QuestionType("FIXTURE", "Fixture", null), null, UUID.randomUUID());
        return new QuestionVersion(question, 1, null, "Prompt", "Explanation", null);
    }

    @Test
    void addsStructurallyValidBatchInStableOrderAndCorrectVersion() {
        var version = draft();
        var result = version.addChoices(List.of(new QuestionChoiceDraft("B", "B", false, 2),
                new QuestionChoiceDraft("A", "A", true, 1)));
        assertEquals(List.of("A", "B"), version.getChoices().stream().map(QuestionChoice::getLabel).toList());
        assertTrue(result.stream().allMatch(choice -> choice.getQuestionVersionId().equals(version.getQuestionVersionId())));
        assertEquals(1, version.answerKey().correctChoiceIds().size());
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test
    void duplicateWithinBatchDoesNotPartiallyAddChoices() {
        var version = draft();
        assertThrows(IllegalArgumentException.class, () -> version.addChoices(List.of(
                new QuestionChoiceDraft("A", "A", true, 1), new QuestionChoiceDraft("A", "Other", false, 2))));
        assertTrue(version.getChoices().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> version.addChoices(List.of(
                new QuestionChoiceDraft("A", "A", true, 1), new QuestionChoiceDraft("B", "Other", false, 1))));
        assertTrue(version.getChoices().isEmpty());
    }

    @Test
    void duplicateAgainstExistingChoiceDoesNotAddEarlierValidInput() {
        var version = draft();
        var original = version.addChoice("A", "Original", true, 1);
        assertThrows(IllegalArgumentException.class, () -> version.addChoices(List.of(
                new QuestionChoiceDraft("B", "B", false, 2), new QuestionChoiceDraft("A", "Other", false, 3))));
        assertEquals(List.of(original), version.getChoices());
    }

    @Test
    void nullBatchOrItemDoesNotPartiallyMutate() {
        var version = draft();
        assertThrows(IllegalArgumentException.class, () -> version.addChoices(null));
        assertThrows(IllegalArgumentException.class, () -> version.addChoices(Arrays.asList(
                new QuestionChoiceDraft("A", "A", true, 1), null)));
        assertTrue(version.getChoices().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_REVIEW", "APPROVED", "PUBLISHED", "ARCHIVED"})
    void nonDraftNeverChangesEvenForEmptyInput(String status) {
        var version = draft();
        ReflectionTestUtils.setField(version, "status", status);
        assertThrows(IllegalStateException.class, () -> version.addChoices(List.of()));
        assertTrue(version.getChoices().isEmpty());
    }

    @Test
    void inputAndOutputDoNotExposeMutableCollectionsOrAnswerTextInLogs() {
        var version = draft();
        var input = new ArrayList<>(List.of(new QuestionChoiceDraft("A", "Secret", true, 1)));
        var result = version.addChoices(input);
        input.clear();
        assertEquals(1, result.size());
        assertEquals("QuestionChoiceDraft[redacted]", new QuestionChoiceDraft("A", "Secret", true, 1).toString());
    }

    @Test
    void structuralValidationDoesNotInventSingleChoiceOrPartRules() {
        var version = draft();
        version.addChoices(List.of(new QuestionChoiceDraft("A", "A", true, 1),
                new QuestionChoiceDraft("B", "B", true, 2)));
        assertEquals(2, version.answerKey().correctChoiceIds().size());
    }

    @Test
    void rejectsStructurallyInvalidInputBeforeItCanReachAggregate() {
        assertThrows(IllegalArgumentException.class, () -> new QuestionChoiceDraft("", "A", false, 1));
        assertThrows(IllegalArgumentException.class, () -> new QuestionChoiceDraft("A", " ", false, 1));
        assertThrows(IllegalArgumentException.class, () -> new QuestionChoiceDraft("A", "A", false, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuestionChoiceDraft("x".repeat(11), "A", false, 1));
    }
}
