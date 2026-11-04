
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.VocabularyService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vocabularies")
public class VocabularyController {

    private final VocabularyService vocabularyService;

    public VocabularyController(
            VocabularyService vocabularyService
    ) {
        this.vocabularyService = vocabularyService;
    }

    // 1. CREATE VOCABULARY
    @PostMapping
    public ResponseEntity<VocabularyResponse> create(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody CreateVocabularyRequest request
    ) {
        VocabularyResponse response = vocabularyService.create(
                principal.userId(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 2. GET ALL + SEARCH
    @GetMapping
    public Page<VocabularyResponse> getAll(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pagination parameters"
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        return vocabularyService.getAll(
                principal.userId(),
                keyword,
                pageable
        );
    }

    // 3. GET VOCABULARY BY ID
    @GetMapping("/{id}")
    public VocabularyResponse getById(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id
    ) {
        return vocabularyService.getById(
                principal.userId(),
                id
        );
    }

    // 4. UPDATE VOCABULARY
    @PutMapping("/{id}")
    public VocabularyResponse update(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVocabularyRequest request
    ) {
        return vocabularyService.update(
                principal.userId(),
                id,
                request
        );
    }

    // 5. DELETE VOCABULARY
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id
    ) {
        vocabularyService.delete(
                principal.userId(),
                id
        );

        return ResponseEntity.noContent().build();
    }
}
