
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.VocabularyResponse;
import com.toeic.backend.vocabulary.domain.DeckVocabulary;
import com.toeic.backend.vocabulary.infrastructure.DeckVocabularyRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyDeckRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeckFlashcardService {

    private final VocabularyDeckRepository deckRepository;
    private final VocabularyRepository vocabularyRepository;
    private final DeckVocabularyRepository deckVocabularyRepository;

    public DeckFlashcardService(
            VocabularyDeckRepository deckRepository,
            VocabularyRepository vocabularyRepository,
            DeckVocabularyRepository deckVocabularyRepository
    ) {
        this.deckRepository = deckRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.deckVocabularyRepository = deckVocabularyRepository;
    }

    // 1. ADD VOCABULARY TO DECK
    @Transactional
    public void addVocabulary(
            UUID currentUserId,
            UUID deckId,
            UUID vocabularyId
    ) {
        checkOwnedDeck(currentUserId, deckId);
        vocabularyRepository.findOwnedByIdForUpdate(vocabularyId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Vocabulary not found"));

        if (deckVocabularyRepository
                .existsByIdDeckIdAndIdVocabularyId(
                        deckId,
                        vocabularyId
                )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vocabulary already exists in deck"
            );
        }

        DeckVocabulary link = new DeckVocabulary(
                deckId,
                vocabularyId
        );

        deckVocabularyRepository.save(link);
    }

    // 2. REMOVE VOCABULARY FROM DECK
    @Transactional
    public void removeVocabulary(
            UUID currentUserId,
            UUID deckId,
            UUID vocabularyId
    ) {
        checkOwnedDeck(currentUserId, deckId);

        if (!deckVocabularyRepository
                .existsByIdDeckIdAndIdVocabularyId(
                        deckId,
                        vocabularyId
                )) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Vocabulary not found in deck"
            );
        }

        deckVocabularyRepository
                .deleteByIdDeckIdAndIdVocabularyId(
                        deckId,
                        vocabularyId
                );
    }

    // 3. GET FLASHCARDS IN DECK
    @Transactional(readOnly = true)
    public List<VocabularyResponse> getFlashcards(
            UUID currentUserId,
            UUID deckId
    ) {
        checkOwnedDeck(currentUserId, deckId);

        return deckVocabularyRepository
                .findByIdDeckIdOrderByAddedAtDesc(deckId)
                .stream()
                .map(link -> vocabularyRepository.findById(link.getVocabularyId()))
                .flatMap(Optional::stream)
                .filter(vocabulary -> currentUserId.equals(vocabulary.getCreatedBy()))
                .map(VocabularyResponse::from)
                .toList();
    }

    // 4. CHECK DECK OWNERSHIP
    private void checkOwnedDeck(
            UUID currentUserId,
            UUID deckId
    ) {
        deckRepository
                .findByDeckIdAndUserId(deckId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vocabulary deck not found"
                ));
    }

}
