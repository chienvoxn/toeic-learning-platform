package com.toeic.backend.vocabulary.api;

import tools.jackson.databind.JsonNode;
import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.VocabularyLearningSessionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vocabulary-decks/{deckId}/learning-session")
public class VocabularyLearningSessionController {
    private final VocabularyLearningSessionService service;
    public VocabularyLearningSessionController(VocabularyLearningSessionService service) { this.service = service; }

    @GetMapping
    public VocabularyLearningSessionService.Snapshot get(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID deckId) {
        return service.get(principal.userId(), deckId);
    }

    @PutMapping
    public VocabularyLearningSessionService.Snapshot save(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID deckId,
            @RequestBody JsonNode data) {
        return service.save(principal.userId(), deckId, data);
    }

    @DeleteMapping
    public void clear(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID deckId) {
        service.clear(principal.userId(), deckId);
    }
}
