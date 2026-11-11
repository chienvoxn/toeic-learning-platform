package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.CatalogDeckResponse;
import com.toeic.backend.vocabulary.api.CatalogVocabularyResponse;
import com.toeic.backend.vocabulary.domain.SystemVocabularyDeck;
import com.toeic.backend.vocabulary.infrastructure.SystemVocabularyDeckRepository;
import com.toeic.backend.vocabulary.infrastructure.SystemVocabularyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SystemVocabularyCatalogService {

    private final SystemVocabularyDeckRepository deckRepository;
    private final SystemVocabularyRepository vocabularyRepository;

    public SystemVocabularyCatalogService(
            SystemVocabularyDeckRepository deckRepository,
            SystemVocabularyRepository vocabularyRepository
    ) {
        this.deckRepository = deckRepository;
        this.vocabularyRepository = vocabularyRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogDeckResponse> getPublishedDecks() {
        return deckRepository.findByPublishedTrueOrderByNameAsc()
                .stream()
                .map(CatalogDeckResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogDeckResponse getDeck(UUID deckId) {
        return CatalogDeckResponse.from(findPublishedDeck(deckId));
    }

    @Transactional(readOnly = true)
    public List<CatalogVocabularyResponse> getFlashcards(UUID deckId) {
        findPublishedDeck(deckId);

        return vocabularyRepository.findBySystemDeckId(deckId)
                .stream()
                .map(CatalogVocabularyResponse::from)
                .toList();
    }

    private SystemVocabularyDeck findPublishedDeck(UUID deckId) {
        return deckRepository.findByDeckIdAndPublishedTrue(deckId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Catalog deck not found"
                ));
    }
}
