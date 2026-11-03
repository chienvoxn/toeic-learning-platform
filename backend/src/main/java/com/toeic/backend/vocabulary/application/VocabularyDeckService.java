
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.CreateDeckRequest;
import com.toeic.backend.vocabulary.api.DeckResponse;
import com.toeic.backend.vocabulary.domain.VocabularyDeck;
import com.toeic.backend.vocabulary.infrastructure.VocabularyDeckRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class VocabularyDeckService {

    private final VocabularyDeckRepository deckRepository;

    public VocabularyDeckService(
            VocabularyDeckRepository deckRepository
    ) {
        this.deckRepository = deckRepository;
    }

    // 1. CREATE DECK
    @Transactional
    public DeckResponse create(
            UUID currentUserId,
            CreateDeckRequest request
    ) {
        VocabularyDeck deck = new VocabularyDeck(
                currentUserId,
                request.name().trim(),
                request.description()
        );

        VocabularyDeck saved = deckRepository.save(deck);
        return DeckResponse.from(saved);
    }

    // 2. GET ALL DECKS
    @Transactional(readOnly = true)
    public List<DeckResponse> getAll(UUID currentUserId) {
        return deckRepository
                .findByUserIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(DeckResponse::from)
                .toList();
    }

    // 3. GET DECK BY ID
    @Transactional(readOnly = true)
    public DeckResponse getById(
            UUID currentUserId,
            UUID deckId
    ) {
        return DeckResponse.from(
                findOwnedDeck(currentUserId, deckId)
        );
    }

    // 4. UPDATE DECK
    @Transactional
    public DeckResponse update(
            UUID currentUserId,
            UUID deckId,
            CreateDeckRequest request
    ) {
        VocabularyDeck deck = findOwnedDeck(
                currentUserId,
                deckId
        );

        deck.setName(request.name().trim());
        deck.setDescription(request.description());

        VocabularyDeck saved = deckRepository.saveAndFlush(deck);
        return DeckResponse.from(saved);
    }

    // 5. DELETE DECK
    @Transactional
    public void delete(
            UUID currentUserId,
            UUID deckId
    ) {
        VocabularyDeck deck = findOwnedDeck(
                currentUserId,
                deckId
        );

        deckRepository.delete(deck);
    }

    // 6. CHECK OWNERSHIP
    private VocabularyDeck findOwnedDeck(
            UUID currentUserId,
            UUID deckId
    ) {
        return deckRepository
                .findByDeckIdAndUserId(deckId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vocabulary deck not found"
                ));
    }
}
