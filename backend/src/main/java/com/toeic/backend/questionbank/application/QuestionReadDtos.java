package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.QuestionAssetMetadata;
import com.toeic.backend.questionbank.domain.QuestionVersion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class QuestionReadDtos {
    private QuestionReadDtos() { }

    public record Part(Integer partId, int partNumber, String skill, String name) { }
    public record Topic(Integer topicId, String name) { }
    public record Summary(UUID questionId, UUID questionVersionId, int versionNumber, Part part, Topic topic,
                          String questionType, String difficulty, String questionText, OffsetDateTime publishedAt) {
        static Summary from(QuestionVersion version) {
            var question = version.getQuestion();
            var part = question.getPart();
            var topic = question.getTopic();
            return new Summary(question.getQuestionId(), version.getQuestionVersionId(), version.getVersionNumber(),
                    new Part(part.getPartId(), part.getPartNumber(), part.getSkill(), part.getName()),
                    topic == null ? null : new Topic(topic.getTopicId(), topic.getName()), question.getType().getCode(),
                    version.getDifficulty(), version.getQuestionText(), version.getPublishedAt());
        }
    }
    public record Asset(UUID resourceId, String resourceType, String mimeType, Long fileSize,
                        Integer durationSeconds, Integer displayOrder) {
        static Asset from(QuestionAssetMetadata metadata, Integer order) {
            return new Asset(metadata.resourceId(), metadata.resourceType(), metadata.mimeType(), metadata.fileSize(),
                    metadata.durationSeconds(), order);
        }
    }
    public record Passage(UUID groupId, UUID groupVersionId, int versionNumber, String instruction,
                          String passageText, List<Asset> assets) {
        public Passage { assets = List.copyOf(assets); }
    }
    public record Detail(UUID questionId, UUID questionVersionId, int versionNumber, Part part, Topic topic,
                         String questionType, String difficulty, String questionText, OffsetDateTime publishedAt,
                         List<QuestionPrompt.Choice> choices, List<Asset> assets, Passage passage) {
        public Detail { choices = List.copyOf(choices); assets = List.copyOf(assets); }
        static Detail from(QuestionVersion version, QuestionPrompt prompt,
                           List<QuestionAssetMetadata> questionMedia, List<QuestionAssetMetadata> groupMedia) {
            var summary = Summary.from(version);
            Map<UUID, QuestionAssetMetadata> questionMetadata = questionMedia.stream()
                    .collect(Collectors.toMap(QuestionAssetMetadata::resourceId, Function.identity()));
            var assets = prompt.resourceIds().stream().filter(questionMetadata::containsKey)
                    .map(id -> Asset.from(questionMetadata.get(id), null)).toList();
            Map<UUID, QuestionAssetMetadata> groupMetadata = groupMedia.stream()
                    .collect(Collectors.toMap(QuestionAssetMetadata::resourceId, Function.identity()));
            var group = prompt.passage();
            var passage = group == null ? null : new Passage(version.getQuestion().getGroup().getGroupId(),
                    group.groupVersionId(), group.versionNumber(), group.instruction(), group.passageText(),
                    group.resources().stream().filter(link -> groupMetadata.containsKey(link.resourceId()))
                            .map(link -> Asset.from(groupMetadata.get(link.resourceId()), link.displayOrder())).toList());
            return new Detail(summary.questionId(), summary.questionVersionId(), summary.versionNumber(), summary.part(),
                    summary.topic(), summary.questionType(), summary.difficulty(), summary.questionText(), summary.publishedAt(),
                    prompt.choices(), assets, passage);
        }
    }
    public record QuestionPage(List<Summary> content, int page, int size, long totalElements, int totalPages) {
        public QuestionPage { content = List.copyOf(content); }
    }
}
