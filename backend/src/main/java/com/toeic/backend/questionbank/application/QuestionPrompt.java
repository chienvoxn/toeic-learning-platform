package com.toeic.backend.questionbank.application;

import java.util.List;
import java.util.UUID;

/** Cấu trúc allowlist cho Q-02 sau này; không quyết định quyền công bố câu hỏi. */
public record QuestionPrompt(UUID questionId, UUID questionVersionId, int versionNumber, int partNumber,
                             String topic, String questionType, String difficulty, String questionText,
                             List<Choice> choices, List<UUID> resourceIds, Passage passage) {
    public QuestionPrompt {
        choices = List.copyOf(choices);
        resourceIds = List.copyOf(resourceIds);
    }
    public record Choice(UUID choiceId, String label, String content, int displayOrder) { }
    public record Passage(UUID groupVersionId, int versionNumber, String instruction, String passageText,
                          List<Resource> resources) {
        public Passage { resources = List.copyOf(resources); }
    }
    public record Resource(UUID resourceId, int displayOrder) { }
}
