package com.toeic.backend.listening.application;

import com.toeic.backend.listening.domain.ListeningMediaDtos;
import com.toeic.backend.listening.domain.MediaObjectSpec;
import com.toeic.backend.listening.infrastructure.ListeningMediaRepository;
import com.toeic.backend.listening.infrastructure.S3CompatibleMediaStorageClient;
import com.toeic.backend.questionbank.application.QuestionAuthoringAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý upload file audio/image lên MinIO (S3 API), lưu metadata vào bảng media_resources
 * và sinh Pre-signed URL cho giáo viên/quản trị viên khi soạn thảo câu hỏi TOEIC Part 1-4.
 */
@Service
@PreAuthorize("hasAnyAuthority('question:manage:self','question:manage:any','content:manage:self','content:manage:any') and @questionAuthoringAccess.isActor(#actorId)")
public class TeacherMediaStorageService {
    private final ListeningMediaRepository repository;
    private final S3CompatibleMediaStorageClient storageClient;
    private final QuestionAuthoringAccess access;
    private final Clock clock;

    public TeacherMediaStorageService(
            ListeningMediaRepository repository,
            S3CompatibleMediaStorageClient storageClient,
            QuestionAuthoringAccess access,
            Clock clock
    ) {
        this.repository = repository;
        this.storageClient = storageClient;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ListeningMediaDtos.UploadedMedia upload(
            UUID actorId,
            String resourceType,
            String mimeType,
            byte[] content,
            Integer durationSeconds,
            String visibility
    ) {
        if (content == null) {
            throw ListeningMediaException.invalid();
        }
        String normalizedType;
        String normalizedMime;
        String normalizedVisibility;
        Integer validatedDuration;
        try {
            normalizedType = MediaObjectSpec.normalizeResourceType(resourceType);
            normalizedMime = MediaObjectSpec.normalizeMimeType(normalizedType, mimeType);
            normalizedVisibility = MediaObjectSpec.normalizeVisibility(visibility);
            validatedDuration = MediaObjectSpec.validateDuration(normalizedType, durationSeconds);
            MediaObjectSpec.validateSize(content.length);
        } catch (IllegalArgumentException ex) {
            throw ListeningMediaException.invalid();
        }

        UUID mediaId = UUID.randomUUID();
        String objectKey = MediaObjectSpec.buildObjectKey(normalizedType, normalizedMime, mediaId);
        Instant now = Instant.now(clock);
        try {
            storageClient.putObject(objectKey, normalizedMime, content, now);
        } catch (IllegalStateException ex) {
            throw ListeningMediaException.storageUnavailable();
        }

        OffsetDateTime createdAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        repository.insertMedia(
                mediaId,
                normalizedType,
                objectKey,
                normalizedMime,
                content.length,
                validatedDuration,
                normalizedVisibility,
                actorId,
                createdAt
        );
        var presigned = storageClient.presignGetObject(mediaId, objectKey, now);
        return new ListeningMediaDtos.UploadedMedia(
                mediaId,
                normalizedType,
                normalizedMime,
                content.length,
                validatedDuration,
                normalizedVisibility,
                presigned.url(),
                presigned.expiresAt(),
                createdAt
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ListeningMediaDtos.PresignedMedia presign(UUID actorId, UUID mediaId) {
        if (mediaId == null) {
            throw ListeningMediaException.invalid();
        }
        var row = repository.findAuthorAccessibleMedia(mediaId, actorId, access.any())
                .orElseThrow(ListeningMediaException::mediaNotFound);
        Instant now = Instant.now(clock);
        var presigned = storageClient.presignGetObject(row.resourceId(), row.storageUrl(), now);
        return new ListeningMediaDtos.PresignedMedia(
                row.resourceId(),
                row.resourceType(),
                row.mimeType(),
                row.fileSize(),
                row.durationSeconds(),
                presigned.url(),
                presigned.expiresAt()
        );
    }
}
