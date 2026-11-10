package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PracticeQuestionViewMapperTest {
    private final PracticeQuestionViewMapper mapper = new PracticeQuestionViewMapper(new QuestionPromptMapper());
    private final ObjectMapper json = new ObjectMapper();
    private QuestionVersion version(int number, QuestionGroup group, QuestionGroupVersion groupVersion) {
        var question = new Question(new ToeicPart(3, "LISTENING", "Fixture"), null,
                new QuestionType("SINGLE_CHOICE", "Single", null), group, UUID.randomUUID());
        var version = new QuestionVersion(question, number, groupVersion, "Prompt " + number, "SECRET_EXPLANATION", "EASY");
        version.addChoices(List.of(new QuestionChoiceDraft("B", "Second", false, 2), new QuestionChoiceDraft("A", "First", true, 1)));
        return version;
    }
    @Test void practicePayloadIsAnAllowlistWithoutAnswersExplanationOrTranscript() {
        var version = version(1, null, null);
        var view = mapper.from(version, List.of(), List.of());
        String value = json.writeValueAsString(view);
        for (var hidden : List.of("isCorrect", "correct", "explanation", "SECRET_EXPLANATION", "transcript", "storage", "password", "token")) {
            assertFalse(value.contains(hidden), hidden);
        }
        assertEquals(List.of("A", "B"), view.choices().stream().map(QuestionPrompt.Choice::label).toList());
        assertEquals(version.getQuestionVersionId(), view.questionVersionId());
    }
    @Test void onlyMediaOnExactVersionAndExplicitAclListsAreProjected() {
        var group = new QuestionGroup("PASSAGE");
        var gv = new QuestionGroupVersion(group, 2, "Instructions", "Passage");
        var visible = UUID.randomUUID(); var hidden = UUID.randomUUID(); var unrelated = UUID.randomUUID();
        gv.addResource(visible, 1); gv.addResource(hidden, 2);
        var version = version(3, group, gv);
        version.addResource(visible); version.addResource(hidden);
        var metadata = new QuestionAssetMetadata(visible, "AUDIO", "audio/mpeg", 120L, 5);
        var other = new QuestionAssetMetadata(unrelated, "IMAGE", "image/png", 10L, null);
        var view = mapper.from(version, List.of(metadata, other), List.of(metadata, other));
        assertEquals(List.of(visible), view.assets().stream().map(PracticeQuestionViewMapper.Asset::resourceId).toList());
        assertEquals(List.of(visible), view.passage().assets().stream().map(PracticeQuestionViewMapper.Asset::resourceId).toList());
        assertEquals(gv.getGroupVersionId(), view.passage().groupVersionId());
        String value = json.writeValueAsString(view);
        assertFalse(value.contains(hidden.toString())); assertFalse(value.contains(unrelated.toString()));
        assertTrue(mapper.from(version, List.of(), List.of()).assets().isEmpty());
        assertTrue(mapper.from(version, List.of(), List.of()).passage().assets().isEmpty());
    }
}
