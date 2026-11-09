package com.toeic.backend.questionbank.application;

import java.time.OffsetDateTime;
import java.util.*;

public final class QuestionAuthoringDtos {
    private QuestionAuthoringDtos() {}
    public record QuestionSummary(UUID questionId,Integer partId,Integer topicId,String questionType,UUID groupId,
            String status,UUID publishedVersionId,OffsetDateTime createdAt,OffsetDateTime updatedAt) {}
    public record GroupSummary(UUID groupId,String groupType,String status,UUID publishedVersionId,OffsetDateTime createdAt,OffsetDateTime updatedAt) {}
    public record Asset(UUID mediaId,String resourceType,String mimeType,Long fileSize,Integer durationSeconds,Integer displayOrder,String transcript) {}
    public record Version(UUID questionVersionId,int versionNumber,String status,String questionText,String explanation,String difficulty,
            UUID groupVersionId,QuestionPrompt.Passage passage,List<QuestionAuthoringSnapshotMapper.Choice> choices,List<Asset> assets,List<Asset> passageAssets) {
        public Version { choices=List.copyOf(choices); assets=List.copyOf(assets); passageAssets=List.copyOf(passageAssets); }
        @Override public String toString(){return "ManagementQuestionVersion[redacted]";}
    }
    public record GroupVersion(UUID groupVersionId,int versionNumber,String status,String instruction,String passageText,
            List<Asset> assets,OffsetDateTime createdAt,OffsetDateTime publishedAt,OffsetDateTime archivedAt) {
        public GroupVersion { assets=List.copyOf(assets); }
    }
    public record QuestionDetail(QuestionSummary question,List<Version> versions) { public QuestionDetail { versions=List.copyOf(versions); } }
    public record GroupDetail(GroupSummary group,List<GroupVersion> versions) { public GroupDetail { versions=List.copyOf(versions); } }
    public record Page<T>(List<T> content,int page,int size,long totalElements,int totalPages) { public Page { content=List.copyOf(content); } }
}
