package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class QuestionAuthoringSnapshotMapperTest {
    private final QuestionPromptMapper read = new QuestionPromptMapper();
    private final QuestionAuthoringSnapshotMapper management = new QuestionAuthoringSnapshotMapper(read);
    private final ObjectMapper json = new ObjectMapper();

    private QuestionVersion version(int number, QuestionGroup group, QuestionGroupVersion groupVersion) {
        var question = new Question(new ToeicPart(1, "LISTENING", "Fixture"), null,
                new QuestionType("FIXTURE", "Fixture", null), group, UUID.randomUUID());
        var version = new QuestionVersion(question, number, groupVersion, "Prompt " + number,
                "HIDDEN_EXPLANATION_" + number, null);
        version.addChoices(List.of(new QuestionChoiceDraft("B", "B", false, 2),
                new QuestionChoiceDraft("A", "A", true, 1)));
        version.addResource(UUID.randomUUID());
        return version;
    }

    @Test
    void managementAnswersBelongToExactVersionWithoutChangingSafeReadProjection() throws Exception {
        var version = version(1, null, null);
        var projection = management.from(version);
        assertEquals(version.getQuestionVersionId(), projection.prompt().questionVersionId());
        assertEquals("HIDDEN_EXPLANATION_1", projection.explanation());
        assertEquals(List.of("A", "B"), projection.choices().stream().map(element -> element.label()).toList());
        assertTrue(projection.choices().stream().allMatch(choice -> choice.questionVersionId().equals(version.getQuestionVersionId())));
        assertTrue(projection.choices().getFirst().isCorrect());
        String managementJson = json.writeValueAsString(projection);
        assertTrue(managementJson.contains("isCorrect"));
        assertTrue(managementJson.contains("HIDDEN_EXPLANATION_1"));
        String readJson = json.writeValueAsString(read.toPrompt(version));
        assertFalse(readJson.contains("isCorrect"));
        assertFalse(readJson.contains("explanation"));
        assertFalse(readJson.contains("HIDDEN_EXPLANATION_1"));
    }

    @Test
    void neverAssumesMediaPermissionFromKnownResourceOrPassage() throws Exception {
        var group = new QuestionGroup("PASSAGE");
        var groupVersion = new QuestionGroupVersion(group, 1, "Instruction", "Passage");
        UUID groupMedia = UUID.randomUUID();
        groupVersion.addResource(groupMedia, 1);
        var version = version(1, group, groupVersion);
        var projection = management.from(version);
        assertEquals(groupVersion.getGroupVersionId(), projection.prompt().passage().groupVersionId());
        assertTrue(projection.prompt().resourceIds().isEmpty());
        assertTrue(projection.prompt().passage().resources().isEmpty());
        String serialized = json.writeValueAsString(projection);
        assertFalse(serialized.contains(groupMedia.toString()));
        assertFalse(serialized.contains(version.getResources().getFirst().getResourceId().toString()));
        assertFalse(serialized.contains("transcript"));
        assertFalse(serialized.contains("storage"));
    }

    @Test
    void snapshotsDoNotShareChoiceIdsOrAnswersAcrossVersions() {
        var first = version(1, null, null);
        var second = new QuestionVersion(first.getQuestion(), 2, null, "Other prompt", "OTHER_EXPLANATION", "HARD");
        second.addChoice("C", "Other choice", true, 1);
        var firstDto = management.from(first);
        var secondDto = management.from(second);
        assertNotEquals(firstDto.prompt().questionVersionId(), secondDto.prompt().questionVersionId());
        assertEquals("HIDDEN_EXPLANATION_1", firstDto.explanation());
        assertEquals("OTHER_EXPLANATION", secondDto.explanation());
        assertTrue(java.util.Collections.disjoint(firstDto.choices().stream().map(element -> element.choiceId()).toList(),
                secondDto.choices().stream().map(element -> element.choiceId()).toList()));
        assertThrows(UnsupportedOperationException.class, () -> firstDto.choices().clear());
        assertEquals("QuestionAuthoringSnapshot[redacted]", firstDto.toString());
        assertEquals("AuthoringChoice[redacted]", firstDto.choices().getFirst().toString());
    }
}
