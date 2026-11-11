
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.VocabularyDeck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VocabularyDeckRepository
        extends JpaRepository<VocabularyDeck, UUID> {

    List<VocabularyDeck> findByUserIdOrderByCreatedAtDesc(
            UUID userId
    );

    Optional<VocabularyDeck> findByDeckIdAndUserId(
            UUID deckId,
            UUID userId
    );
}
