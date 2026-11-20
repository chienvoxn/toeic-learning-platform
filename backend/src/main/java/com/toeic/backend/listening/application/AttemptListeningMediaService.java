package com.toeic.backend.listening.application;

import com.toeic.backend.assessment.application.PracticeAccess;
import com.toeic.backend.listening.domain.ListeningMediaDtos;
import com.toeic.backend.listening.infrastructure.ListeningMediaRepository;
import com.toeic.backend.listening.infrastructure.S3CompatibleMediaStorageClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truy xuất danh sách media (AUDIO/IMAGE) kèm Pre-signed URL cho phiên làm bài (Practice / Section Listening).
 * Đảm bảo ẩn hoàn toàn transcript khi attempt đang IN_PROGRESS và chỉ trả transcript sau khi đã GRADED (submit).
 */
@Service
@PreAuthorize("hasAnyAuthority('practice:manage:self','exam:manage:self') and @practiceAccess.isActor(#actorId)")
public class AttemptListeningMediaService {
    private final ListeningMediaRepository repository;
    private final S3CompatibleMediaStorageClient storageClient;
    private final PracticeAccess practiceAccess;
    private final Clock clock;

    public AttemptListeningMediaService(
            ListeningMediaRepository repository,
            S3CompatibleMediaStorageClient storageClient,
            PracticeAccess practiceAccess,
            Clock clock
    ) {
        this.repository = repository;
        this.storageClient = storageClient;
        this.practiceAccess = practiceAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ListeningMediaDtos.AttemptMedia getAttemptMedia(UUID actorId, UUID attemptId) {
        var header = repository.findOwnAttempt(actorId, attemptId)
                .orElseThrow(ListeningMediaException::attemptNotFound);
        practiceAccess.requireSource(actorId, header.sourceType());
        boolean includeTranscript = "GRADED".equals(header.status());
        Instant now = Instant.now(clock);

        var pins = repository.findAttemptPins(attemptId);
        Map<UUID, List<ListeningMediaDtos.AttemptAsset>> groupCache = new HashMap<>();
        List<ListeningMediaDtos.QuestionMedia> questions = new ArrayList<>(pins.size());

        for (var pin : pins) {
            var qAssets = repository.findReadableQuestionAssets(pin.questionVersionId()).stream()
                    .map(row -> toAttemptAsset(attemptId, row, includeTranscript, now))
                    .toList();
            List<ListeningMediaDtos.AttemptAsset> gAssets = List.of();
            if (pin.groupVersionId() != null) {
                gAssets = groupCache.computeIfAbsent(pin.groupVersionId(), groupVersionId ->
                        repository.findReadableGroupAssets(groupVersionId).stream()
                                .map(row -> toAttemptAsset(attemptId, row, includeTranscript, now))
                                .toList()
                );
            }
            questions.add(new ListeningMediaDtos.QuestionMedia(
                    pin.questionOrder(),
                    pin.questionVersionId(),
                    pin.partNumber(),
                    qAssets,
                    pin.groupVersionId(),
                    gAssets
            ));
        }

        return new ListeningMediaDtos.AttemptMedia(
                header.attemptId(),
                header.sourceType(),
                header.status(),
                questions
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ListeningMediaDtos.StreamedContent streamAttemptMediaContent(UUID actorId, UUID attemptId, UUID resourceId) {
        var header = repository.findOwnAttempt(actorId, attemptId)
                .orElseThrow(ListeningMediaException::attemptNotFound);
        practiceAccess.requireSource(actorId, header.sourceType());

        ListeningMediaRepository.LinkedAssetRow matched = null;
        for (var pin : repository.findAttemptPins(attemptId)) {
            for (var asset : repository.findReadableQuestionAssets(pin.questionVersionId())) {
                if (asset.resourceId().equals(resourceId)) {
                    matched = asset;
                    break;
                }
            }
            if (matched != null) {
                break;
            }
            if (pin.groupVersionId() != null) {
                for (var asset : repository.findReadableGroupAssets(pin.groupVersionId())) {
                    if (asset.resourceId().equals(resourceId)) {
                        matched = asset;
                        break;
                    }
                }
            }
            if (matched != null) {
                break;
            }
        }
        if (matched == null) {
            throw ListeningMediaException.mediaNotFound();
        }

        byte[] bytes = storageClient.loadObjectOrDemoFallback(
                matched.resourceId(),
                matched.storageUrl(),
                matched.resourceType(),
                matched.durationSeconds(),
                Instant.now(clock)
        );
        String mimeType = detectEffectiveMimeType(matched.mimeType(), bytes);
        return new ListeningMediaDtos.StreamedContent(mimeType, bytes);
    }

    private ListeningMediaDtos.AttemptAsset toAttemptAsset(
            UUID attemptId,
            ListeningMediaRepository.LinkedAssetRow row,
            boolean includeTranscript,
            Instant now
    ) {
        var presigned = storageClient.presignGetObject(row.resourceId(), row.storageUrl(), now);
        String fallbackUrl = "/api/v1/attempts/" + attemptId + "/media/" + row.resourceId() + "/content";
        return new ListeningMediaDtos.AttemptAsset(
                row.resourceId(),
                row.resourceType(),
                row.mimeType(),
                row.fileSize(),
                row.durationSeconds(),
                row.displayOrder(),
                presigned.url(),
                fallbackUrl,
                presigned.expiresAt(),
                includeTranscript ? row.transcript() : null
        );
    }

    private static String detectEffectiveMimeType(String declaredMimeType, byte[] bytes) {
        if (bytes != null && bytes.length >= 4) {
            if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F') {
                return "audio/wav";
            }
            String prefix = new String(bytes, 0, Math.min(bytes.length, 32), StandardCharsets.UTF_8).trim();
            if (prefix.startsWith("<svg")) {
                return "image/svg+xml";
            }
        }
        return declaredMimeType == null || declaredMimeType.isBlank() ? "application/octet-stream" : declaredMimeType;
    }
}
