package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.SystemVocabulary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SystemVocabularyRepository
        extends JpaRepository<SystemVocabulary, UUID> {

    @Query(value = """
            SELECT v.*
            FROM system_vocabularies v
            JOIN system_deck_vocabularies dv
              ON dv.vocabulary_id = v.vocabulary_id
            WHERE dv.deck_id = :deckId
            ORDER BY dv.display_order ASC, v.word ASC
            """, nativeQuery = true)
    List<SystemVocabulary> findBySystemDeckId(
            @Param("deckId") UUID deckId
    );
}
