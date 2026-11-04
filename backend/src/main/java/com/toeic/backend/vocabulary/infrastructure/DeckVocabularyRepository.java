
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.DeckVocabulary;
import com.toeic.backend.vocabulary.domain.DeckVocabularyId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeckVocabularyRepository
        extends JpaRepository<DeckVocabulary, DeckVocabularyId> {

    // Get all vocabulary links in a deck
    List<DeckVocabulary> findByIdDeckIdOrderByAddedAtDesc(
            UUID deckId
    );

    // Check if vocabulary already exists in a deck
    boolean existsByIdDeckIdAndIdVocabularyId(
            UUID deckId,
            UUID vocabularyId
    );

    // Delete a vocabulary link from a deck
    void deleteByIdDeckIdAndIdVocabularyId(
            UUID deckId,
            UUID vocabularyId
    );
}
