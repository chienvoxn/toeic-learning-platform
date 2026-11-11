package com.toeic.backend.questionbank.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionDomainTest {
    private Question question(QuestionGroup group) {
        return new Question(new ToeicPart(1, "LISTENING", "Fixture"), null,
                new QuestionType("FIXTURE", "Fixture", null), group, UUID.randomUUID());
    }
    private QuestionVersion draft() {
        return new QuestionVersion(question(null), 1, null, "Fixture prompt", "HIDDEN_EXPLANATION", "EASY");
    }

    @Test
    void draftAllowsIncompleteAnswerKeyWithoutImposingPublishPolicy() {
        var version = draft();
        assertTrue(version.answerKey().correctChoiceIds().isEmpty());
        var a = version.addChoice("A", "Choice A", true, 2);
        var b = version.addChoice("B", "Choice B", true, 1);
        assertEquals(java.util.Set.of(a.getChoiceId(), b.getChoiceId()), version.answerKey().correctChoiceIds());
        assertEquals("HIDDEN_EXPLANATION", version.answerKey().explanation());
        assertEquals(java.util.List.of("B", "A"), version.getChoices().stream().map(element -> element.getLabel()).toList());
        assertEquals("QuestionAnswerKey[redacted]", version.answerKey().toString());
    }

    @Test
    void rejectsDuplicateChoiceLabelsAndOrdersWithinOneVersion() {
        var version = draft();
        version.addChoice("A", "A", true, 1);
        assertThrows(IllegalArgumentException.class, () -> version.addChoice("A", "Other", false, 2));
        assertThrows(IllegalArgumentException.class, () -> version.addChoice("B", "Other", false, 1));
        assertEquals(1, version.getChoices().size());
    }

    @Test
    void rejectsInvalidChoiceInputsBeforeMutatingAggregate() {
        var version = draft();
        assertThrows(IllegalArgumentException.class, () -> version.addChoice("", "Text", false, 1));
        assertThrows(IllegalArgumentException.class, () -> version.addChoice("12345678901", "Text", false, 1));
        assertThrows(IllegalArgumentException.class, () -> version.addChoice("A", " ", false, 1));
        assertThrows(IllegalArgumentException.class, () -> version.addChoice("A", "Text", false, 0));
        assertTrue(version.getChoices().isEmpty());
    }

    @Test
    void validatesVersionTextDifficultyAndNumberWithoutPartialEdits() {
        var version = draft();
        assertThrows(IllegalArgumentException.class, () -> version.reviseDraft(" ", "Changed", "HARD"));
        assertThrows(IllegalArgumentException.class, () -> version.reviseDraft("Changed", "Changed", "x".repeat(21)));
        assertEquals("Fixture prompt", version.getQuestionText());
        assertEquals("HIDDEN_EXPLANATION", version.getExplanation());
        assertThrows(IllegalArgumentException.class,
                () -> new QuestionVersion(question(null), 0, null, "Text", null, null));
    }

    @Test
    void rejectsForeignGroupVersionAndMissingPinnedGroupVersion() {
        var first = new QuestionGroup("PASSAGE");
        var second = new QuestionGroup("PASSAGE");
        var otherVersion = new QuestionGroupVersion(second, 1, "Instruction", "Passage");
        assertThrows(IllegalArgumentException.class,
                () -> new QuestionVersion(question(first), 1, otherVersion, "Text", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new QuestionVersion(question(first), 1, null, "Text", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new QuestionVersion(question(null), 1, otherVersion, "Text", null, null));
    }

    @Test
    void protectsCollectionsAndAnswerKeySnapshotFromExternalMutation() {
        var version = draft();
        version.addChoice("A", "A", true, 1);
        var key = version.answerKey();
        version.addChoice("B", "B", true, 2);
        assertEquals(1, key.correctChoiceIds().size());
        assertThrows(UnsupportedOperationException.class, () -> key.correctChoiceIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> version.getChoices().clear());
        assertThrows(UnsupportedOperationException.class, () -> version.getResources().add(null));
    }

    @Test
    void validatesDuplicateResourcesAndGroupDisplayOrder() {
        var version = draft();
        var resource = UUID.randomUUID();
        version.addResource(resource);
        assertThrows(IllegalArgumentException.class, () -> version.addResource(resource));
        var group = new QuestionGroupVersion(new QuestionGroup("PASSAGE"), 1, null, null);
        assertThrows(IllegalArgumentException.class, () -> group.addResource(resource, 0));
        group.addResource(resource, 1);
        assertThrows(IllegalArgumentException.class, () -> group.addResource(resource, 2));
        assertThrows(UnsupportedOperationException.class, () -> group.getResources().clear());
    }

    @Test
    void compositeAssetIdsDistinguishVersionsAndSupportSetMembership() {
        var version = UUID.randomUUID();
        var resource = UUID.randomUUID();
        var first = new VersionAssetId(version, resource);
        assertEquals(first, new VersionAssetId(version, resource));
        assertEquals(first.hashCode(), new VersionAssetId(version, resource).hashCode());
        assertNotEquals(first, new VersionAssetId(UUID.randomUUID(), resource));
    }
}
