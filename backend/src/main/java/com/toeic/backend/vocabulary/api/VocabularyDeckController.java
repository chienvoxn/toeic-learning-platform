
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.VocabularyDeckService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vocabulary-decks")
public class VocabularyDeckController {

    private final VocabularyDeckService deckService;

    public VocabularyDeckController(
            VocabularyDeckService deckService
    ) {
        this.deckService = deckService;
    }

    // 1. CREATE DECK
    @PostMapping
    public ResponseEntity<DeckResponse> create(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody CreateDeckRequest request
    ) {
        DeckResponse response = deckService.create(
                principal.userId(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 2. GET ALL DECKS
    @GetMapping
    public List<DeckResponse> getAll(
            @AuthenticationPrincipal SessionPrincipal principal
    ) {
        return deckService.getAll(principal.userId());
    }

    // 3. GET DECK BY ID
    @GetMapping("/{id}")
    public DeckResponse getById(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id
    ) {
        return deckService.getById(
                principal.userId(),
                id
        );
    }

    // 4. UPDATE DECK
    @PutMapping("/{id}")
    public DeckResponse update(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateDeckRequest request
    ) {
        return deckService.update(
                principal.userId(),
                id,
                request
        );
    }

    // 5. DELETE DECK
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id
    ) {
        deckService.delete(
                principal.userId(),
                id
        );

        return ResponseEntity.noContent().build();
    }
}
