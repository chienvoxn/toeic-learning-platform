package com.toeic.backend.listening.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTO allowlist cho upload media (MinIO S3), pre-signed URL và media/transcript theo trạng thái attempt. */
public final class ListeningMediaDtos {
    private ListeningMediaDtos() {
    }

    public record UploadedMedia(
            UUID mediaId,
            String resourceType,
            String mimeType,
            long fileSize,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer durationSeconds,
            String visibility,
            String url,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt
    ) {
    }

    public record PresignedMedia(
            UUID mediaId,
            String resourceType,
            String mimeType,
            Long fileSize,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer durationSeconds,
            String url,
            OffsetDateTime expiresAt
    ) {
    }

    public record AttemptAsset(
            UUID resourceId,
            String resourceType,
            String mimeType,
            Long fileSize,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer durationSeconds,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer displayOrder,
            String url,
            String fallbackUrl,
            OffsetDateTime expiresAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String transcript
    ) {
    }

    public record QuestionMedia(
            int order,
            UUID questionVersionId,
            int partNumber,
            List<AttemptAsset> assets,
            @JsonInclude(JsonInclude.Include.NON_NULL) UUID groupVersionId,
            List<AttemptAsset> passageAssets
    ) {
        public QuestionMedia {
            assets = List.copyOf(assets);
            passageAssets = List.copyOf(passageAssets);
        }
    }

    public record AttemptMedia(
            UUID attemptId,
            String sourceType,
            String status,
            List<QuestionMedia> questions
    ) {
        public AttemptMedia {
            questions = List.copyOf(questions);
        }
    }

    public record StreamedContent(
            String mimeType,
            byte[] bytes
    ) {
    }
}
