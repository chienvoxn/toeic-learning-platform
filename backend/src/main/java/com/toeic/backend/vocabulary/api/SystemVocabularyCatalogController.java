package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.SystemVocabularyCatalogService;
import com.toeic.backend.vocabulary.application.VocabularyCatalogEnrollmentService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vocabulary/catalog/decks")
public class SystemVocabularyCatalogController {

    private final SystemVocabularyCatalogService catalogService;
    private final VocabularyCatalogEnrollmentService enrollmentService;

    public SystemVocabularyCatalogController(
            SystemVocabularyCatalogService catalogService,
            VocabularyCatalogEnrollmentService enrollmentService
    ) {
        this.catalogService = catalogService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public List<CatalogDeckResponse> getDecks() {
        return catalogService.getPublishedDecks();
    }

    @GetMapping("/{deckId}")
    public CatalogDeckResponse getDeck(@PathVariable UUID deckId) {
        return catalogService.getDeck(deckId);
    }

    @GetMapping("/{deckId}/flashcards")
    public List<CatalogVocabularyResponse> getFlashcards(
            @PathVariable UUID deckId
    ) {
        return catalogService.getFlashcards(deckId);
    }

    @PostMapping("/{deckId}/start")
    public CatalogEnrollmentResponse start(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID deckId
    ) {
        return enrollmentService.start(principal.userId(), deckId);
    }
}
