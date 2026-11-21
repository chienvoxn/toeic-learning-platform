package com.toeic.backend.listening.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.listening.application.ListeningMediaException;
import com.toeic.backend.listening.application.TeacherMediaStorageService;
import com.toeic.backend.listening.domain.ListeningMediaDtos;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** API cho giáo viên/admin upload file audio/image lên MinIO và lấy Pre-signed URL. */
@RestController
@RequestMapping("/api/v1/teacher/media")
public class TeacherMediaController {
    private static final Set<String> ALLOWED_UPLOAD_PARAMS = Set.of("resourceType", "visibility", "durationSeconds", "mimeType");
    private final TeacherMediaStorageService service;

    public TeacherMediaController(TeacherMediaStorageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ListeningMediaDtos.UploadedMedia upload(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam MultiValueMap<String, String> params
    ) {
        if (file == null || file.isEmpty()) {
            throw ListeningMediaException.invalid();
        }
        for (var entry : params.entrySet()) {
            if (!ALLOWED_UPLOAD_PARAMS.contains(entry.getKey()) || entry.getValue().size() > 1) {
                throw ListeningMediaException.invalid();
            }
        }
        String resourceType = params.getFirst("resourceType");
        String visibility = params.getFirst("visibility");
        String explicitMime = params.getFirst("mimeType");
        String effectiveMime = (explicitMime != null && !explicitMime.isBlank()) ? explicitMime : file.getContentType();
        Integer durationSeconds = null;
        String rawDuration = params.getFirst("durationSeconds");
        if (rawDuration != null && !rawDuration.isBlank()) {
            try {
                durationSeconds = Integer.valueOf(rawDuration.trim());
            } catch (NumberFormatException ex) {
                throw ListeningMediaException.invalid();
            }
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ListeningMediaException.invalid();
        }
        return service.upload(actor.userId(), resourceType, effectiveMime, bytes, durationSeconds, visibility);
    }

    @GetMapping("/{mediaId}/presign")
    public ListeningMediaDtos.PresignedMedia presign(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID mediaId,
            @RequestParam MultiValueMap<String, String> params
    ) {
        if (!params.isEmpty()) {
            throw ListeningMediaException.invalid();
        }
        return service.presign(actor.userId(), mediaId);
    }
}
