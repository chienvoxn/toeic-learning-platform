package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.SystemVocabularyDeck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemVocabularyDeckRepository
        extends JpaRepository<SystemVocabularyDeck, UUID> {

    List<SystemVocabularyDeck> findByPublishedTrueOrderByNameAsc();

    Optional<SystemVocabularyDeck> findByDeckIdAndPublishedTrue(UUID deckId);
}
