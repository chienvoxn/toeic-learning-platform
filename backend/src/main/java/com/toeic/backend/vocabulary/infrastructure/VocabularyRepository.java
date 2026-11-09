
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.Vocabulary;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VocabularyRepository
        extends JpaRepository<Vocabulary, UUID> {

    // TV3-01: Find vocabularies owned by a user
    Page<Vocabulary> findByCreatedBy(
            UUID createdBy,
            Pageable pageable
    );

    // TV3-01: Search vocabularies by word
    Page<Vocabulary> findByCreatedByAndWordContainingIgnoreCase(
            UUID createdBy,
            String word,
            Pageable pageable
    );

    // TV3-01: Verify vocabulary ownership
    boolean existsByVocabularyIdAndCreatedBy(
            UUID vocabularyId,
            UUID createdBy
    );

    // TV3-04: Total vocabularies owned by a user
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

    /**
     * TV3-06: Find vocabularies that have never been reviewed.
     *
     * Includes vocabularies without a ReviewState and
     * vocabularies whose lastReviewedAt is null.
     *
     * Results are ordered by creation time.
     */
    @Query("""
            SELECT v
            FROM Vocabulary v
            WHERE v.createdBy = :userId
              AND NOT EXISTS (
                  SELECT s.reviewStateId
                  FROM ReviewState s
                  WHERE s.userId = :userId
                    AND s.vocabularyId = v.vocabularyId
                    AND s.lastReviewedAt IS NOT NULL
              )
            ORDER BY v.createdAt ASC, v.vocabularyId ASC
            """)
    List<Vocabulary> findUnreviewedVocabularies(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    /**
     * TV3-06: Fetch vocabularies by IDs,
     * restricted to the current owner.
     */
    List<Vocabulary> findByCreatedByAndVocabularyIdIn(
            UUID createdBy,
            List<UUID> vocabularyIds
    );
}
