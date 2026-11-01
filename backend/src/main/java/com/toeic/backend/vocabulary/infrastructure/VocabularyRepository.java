
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.Vocabulary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VocabularyRepository
        extends JpaRepository<Vocabulary, UUID> {

    Page<Vocabulary> findByCreatedBy(
            UUID createdBy,
            Pageable pageable
    );

    Page<Vocabulary> findByCreatedByAndWordContainingIgnoreCase(
            UUID createdBy,
            String word,
            Pageable pageable
    );

    boolean existsByVocabularyIdAndCreatedBy(
            UUID vocabularyId,
            UUID createdBy
    );
}
