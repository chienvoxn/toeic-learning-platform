package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.QuestionVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Management-only version projection. A future authoring service must authorize actor/ownership
 * before calling this mapper; it is never used by Q-02 and exposes no media URLs/transcript.
 */
@Component
public class QuestionAuthoringSnapshotMapper {
    private final QuestionPromptMapper prompts;

    public QuestionAuthoringSnapshotMapper(QuestionPromptMapper prompts) { this.prompts = prompts; }

    public Snapshot from(QuestionVersion version) {
        // No media is implicitly authorized here. Asset ACL/policy remains a service responsibility.
        var prompt = prompts.toPrompt(version, java.util.Set.of(), java.util.Set.of());
        return new Snapshot(prompt, version.getStatus(), version.getExplanation(),
                version.getChoices().stream().map(choice -> new Choice(choice.getChoiceId(),
                        choice.getQuestionVersionId(), choice.getLabel(), choice.getContent(),
                        choice.isCorrect(), choice.getDisplayOrder())).toList());
    }

    public record Choice(UUID choiceId, UUID questionVersionId, String label, String content,
                         boolean isCorrect, int displayOrder) {
        @Override public String toString() { return "AuthoringChoice[redacted]"; }
    }

    public record Snapshot(QuestionPrompt prompt, String status, String explanation, List<Choice> choices) {
        public Snapshot { choices = List.copyOf(choices); }
        @Override public String toString() { return "QuestionAuthoringSnapshot[redacted]"; }
    }
}
