
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.DeckFlashcardService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
@RestController
@RequestMapping("/api/v1/vocabulary-decks/{deckId}")
public class DeckFlashcardController {

    private final DeckFlashcardService flashcardService;

    public DeckFlashcardController(
            DeckFlashcardService flashcardService
    ) {
        this.flashcardService = flashcardService;
    }

    // 1. ADD VOCABULARY TO DECK
    @PostMapping("/vocabularies")
    public ResponseEntity<Void> addVocabulary(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID deckId,
            @Valid @RequestBody AddDeckVocabularyRequest request
    ) {
        flashcardService.addVocabulary(
                principal.userId(),
                deckId,
                request.vocabularyId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

    // 2. REMOVE VOCABULARY FROM DECK
    @DeleteMapping("/vocabularies/{vocabularyId}")
    public ResponseEntity<Void> removeVocabulary(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID deckId,
            @PathVariable UUID vocabularyId
    ) {
        flashcardService.removeVocabulary(
                principal.userId(),
                deckId,
                vocabularyId
        );

        return ResponseEntity.noContent().build();
    }

    // 3. GET FLASHCARDS IN DECK
    @GetMapping("/flashcards")
    public List<VocabularyResponse> getFlashcards(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID deckId
    ) {
        return flashcardService.getFlashcards(
                principal.userId(),
                deckId
        );
    }
}
