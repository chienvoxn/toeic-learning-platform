package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.QuestionAssetMetadata;
import com.toeic.backend.questionbank.domain.QuestionVersion;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Practice projection; caller must authorize the actor, exact version and supplied media metadata. */
@Component
public class PracticeQuestionViewMapper {
    private final QuestionPromptMapper prompts;
    public PracticeQuestionViewMapper(QuestionPromptMapper prompts) { this.prompts = prompts; }
    public View from(QuestionVersion version, List<QuestionAssetMetadata> questionMedia, List<QuestionAssetMetadata> groupMedia) {
        var q = index(questionMedia);
        var g = index(groupMedia);
        var prompt = prompts.toPrompt(version, q.keySet(), g.keySet());
        var assets = prompt.resourceIds().stream().map(id -> Asset.from(q.get(id), null)).toList();
        var group = prompt.passage();
        var passage = group == null ? null : new Passage(group.groupVersionId(), group.versionNumber(), group.instruction(),
                group.passageText(), group.resources().stream().map(link -> Asset.from(g.get(link.resourceId()), link.displayOrder())).toList());
        return new View(prompt.questionId(), prompt.questionVersionId(), prompt.versionNumber(), prompt.partNumber(),
                prompt.topic(), prompt.questionType(), prompt.difficulty(), prompt.questionText(), prompt.choices(), assets, passage);
    }
    private static Map<UUID, QuestionAssetMetadata> index(List<QuestionAssetMetadata> media) {
        return media.stream().collect(Collectors.toMap(QuestionAssetMetadata::resourceId, Function.identity()));
    }
    public record Asset(UUID resourceId, String resourceType, String mimeType, Long fileSize, Integer durationSeconds, Integer displayOrder) {
        static Asset from(QuestionAssetMetadata m, Integer order) {
            return new Asset(m.resourceId(), m.resourceType(), m.mimeType(), m.fileSize(), m.durationSeconds(), order);
        }
    }
    public record Passage(UUID groupVersionId, int versionNumber, String instruction, String passageText, List<Asset> assets) {
        public Passage { assets = List.copyOf(assets); }
    }
    public record View(UUID questionId, UUID questionVersionId, int versionNumber, int partNumber, String topic,
                       String questionType, String difficulty, String questionText, List<QuestionPrompt.Choice> choices,
                       List<Asset> assets, Passage passage) {
        public View { choices = List.copyOf(choices); assets = List.copyOf(assets); }
    }
}
