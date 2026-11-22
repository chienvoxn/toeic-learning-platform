package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.VocabularyLearningSessionService;
import com.toeic.backend.vocabulary.application.VocabularyLearningSessionService.ReviewResult;
import com.toeic.backend.vocabulary.application.VocabularyLearningSessionService.Snapshot;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vocabulary-decks/{deckId}/learning-session")
public class VocabularyLearningSessionController {
    private final VocabularyLearningSessionService service;

    public VocabularyLearningSessionController(VocabularyLearningSessionService service) {
        this.service = service;
    }

    @GetMapping
    public Snapshot get(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID deckId) {
        return service.get(principal.userId(), deckId);
    }

    @PutMapping
    public Snapshot save(
            @AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID deckId,
            @RequestParam long expectedRevision, @RequestBody JsonNode data
    ) {
        return service.save(principal.userId(), deckId, expectedRevision, data);
    }

    @DeleteMapping
    public Snapshot clear(
            @AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID deckId,
            @RequestParam long expectedRevision
    ) {
        return service.clear(principal.userId(), deckId, expectedRevision);
    }

    @PostMapping("/reviews")
    public ReviewResult rate(
            @AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID deckId,
            @Valid @RequestBody SubmitLearningSessionReviewRequest request
    ) {
        return service.rate(principal.userId(), deckId, request.expectedRevision(), request.vocabularyId(), request.rating());
    }
}
