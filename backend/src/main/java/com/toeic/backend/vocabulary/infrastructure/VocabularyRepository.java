
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.Vocabulary;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
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

    // LP-01: Total vocabularies owned by the user
    long countByCreatedBy(UUID createdBy);

    /**
     * Lock the vocabulary row while processing an SRS review.
     *
     * This serializes concurrent review submissions for the
     * same vocabulary, including the first review when no
     * ReviewState exists yet.
     *
     * Must be called inside an active transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT v
            FROM Vocabulary v
            WHERE v.vocabularyId = :vocabularyId
              AND v.createdBy = :userId
            """)
    Optional<Vocabulary> findOwnedByIdForUpdate(
            @Param("vocabularyId") UUID vocabularyId,
            @Param("userId") UUID userId
    );
}
