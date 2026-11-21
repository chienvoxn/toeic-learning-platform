package com.toeic.backend.listening.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.listening.application.AttemptListeningMediaService;
import com.toeic.backend.listening.application.ListeningMediaException;
import com.toeic.backend.listening.domain.ListeningMediaDtos;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API trả về danh sách media (kèm Pre-signed URL) và transcript (chỉ sau khi submit) của phiên làm bài. */
@RestController
@RequestMapping("/api/v1/attempts")
public class AttemptListeningMediaController {
    private final AttemptListeningMediaService service;

    public AttemptListeningMediaController(AttemptListeningMediaService service) {
        this.service = service;
    }

    @GetMapping("/{id}/media")
    public ListeningMediaDtos.AttemptMedia getAttemptMedia(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> params
    ) {
        if (!params.isEmpty()) {
            throw ListeningMediaException.invalid();
        }
        return service.getAttemptMedia(actor.userId(), id);
    }

    @GetMapping("/{id}/media/{resourceId}/content")
    public ResponseEntity<byte[]> streamAttemptMediaContent(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID resourceId,
            @RequestParam MultiValueMap<String, String> params
    ) {
        if (!params.isEmpty()) {
            throw ListeningMediaException.invalid();
        }
        var content = service.streamAttemptMediaContent(actor.userId(), id, resourceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, content.mimeType() != null ? content.mimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(content.bytes());
    }
}
