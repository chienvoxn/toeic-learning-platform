package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class QuestionReadPromptMapperTest {
    private final QuestionPromptMapper prompts = new QuestionPromptMapper();
    private final ObjectMapper json = new ObjectMapper();

    private QuestionVersion version(QuestionGroupVersion group) {
        var question = new Question(new ToeicPart(1, "LISTENING", "Fixture"), null,
                new QuestionType("Q02_FIXTURE", "Fixture", null), group == null ? null : group.getGroup(), UUID.randomUUID());
        var version = new QuestionVersion(question, 1, group, "Fixture prompt", "ANSWER_SECRET_EXPLANATION", "CUSTOM");
        version.addChoice("B", "Fixture B", false, 2);
        version.addChoice("A", "Fixture A", true, 1);
        return version;
    }

    @Test
    void filteredPromptOmitsForbiddenAssetIdsAndAnswersAcrossTheEntireJson() throws Exception {
        var questionAsset = UUID.randomUUID();
        var privateQuestionAsset = UUID.randomUUID();
        var groupAsset = UUID.randomUUID();
        var privateGroupAsset = UUID.randomUUID();
        var foreignAsset = UUID.randomUUID();
        var group = new QuestionGroupVersion(new QuestionGroup("PASSAGE"), 1, "Instruction", "Pinned passage");
        group.addResource(privateGroupAsset, 1);
        group.addResource(groupAsset, 2);
        var version = version(group);
        version.addResource(privateQuestionAsset);
        version.addResource(questionAsset);

        var prompt = prompts.toPrompt(version, Set.of(questionAsset, foreignAsset), Set.of(groupAsset, foreignAsset));
        var serialized = json.writeValueAsString(prompt);
        assertEquals(List.of(questionAsset), prompt.resourceIds());
        assertEquals(List.of(groupAsset), prompt.passage().resources().stream().map(element -> element.resourceId()).toList());
        assertEquals(2, prompt.passage().resources().getFirst().displayOrder());
        assertEquals(group.getGroupVersionId(), prompt.passage().groupVersionId());
        assertEquals(List.of("A", "B"), prompt.choices().stream().map(element -> element.label()).toList());
        for (var hidden : List.of(privateQuestionAsset.toString(), privateGroupAsset.toString(), foreignAsset.toString(),
                "ANSWER_SECRET_EXPLANATION", "isCorrect", "correctChoiceIds", "answerKey", "explanation", "storageUrl", "transcript")) {
            assertFalse(serialized.contains(hidden), "Unexpected hidden data: " + hidden);
        }
    }

    @Test
    void standalonePromptCanHaveNoReadableAssets() {
        var version = version(null);
        version.addResource(UUID.randomUUID());
        var prompt = prompts.toPrompt(version, Set.of(), Set.of());
        assertTrue(prompt.resourceIds().isEmpty());
        assertNull(prompt.passage());
        assertEquals(2, prompt.choices().size());
    }

    @Test
    void existingQ01InternalMapperContractRemainsAvailable() {
        var asset = UUID.randomUUID();
        var group = new QuestionGroupVersion(new QuestionGroup("PASSAGE"), 1, null, "Passage");
        group.addResource(asset, 1);
        var version = version(group);
        version.addResource(asset);
        var prompt = prompts.toPrompt(version);
        assertEquals(List.of(asset), prompt.resourceIds());
        assertEquals(asset, prompt.passage().resources().getFirst().resourceId());
    }
}
