
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.ReviewState;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewStateRepository
        extends JpaRepository<ReviewState, UUID> {

    // TV3-03: Find review state of a vocabulary
    Optional<ReviewState> findByUserIdAndVocabularyId(
            UUID userId,
            UUID vocabularyId
    );

    // TV3-03: Find vocabularies due for review
    List<ReviewState> findByUserIdAndNextReviewAtLessThanEqual(
            UUID userId,
            OffsetDateTime now
    );

    // TV3-03: Count all review states of a user
    long countByUserId(UUID userId);

    // TV3-03: Count review states due for review
    long countByUserIdAndNextReviewAtLessThanEqual(
            UUID userId,
            OffsetDateTime now
    );

    // TV3-04 (LP-01): Count reviewed vocabularies
    long countByUserIdAndLastReviewedAtIsNotNull(
            UUID userId
    );

    // TV3-04 (LP-01): Count reviewed vocabularies due for review
    long countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(
            UUID userId,
            OffsetDateTime now
    );

    // TV3-04 (LP-01): Count mastered vocabularies
    long countByUserIdAndLastReviewedAtIsNotNullAndRepetitionCountGreaterThanEqual(
            UUID userId,
            int repetitionCount
    );

    /**
     * TV3-06: Find reviewed vocabularies due for review.
     *
     * Only includes review states that have been reviewed
     * at least once.
     *
     * Results are ordered by nextReviewAt ascending,
     * prioritizing the most overdue vocabularies.
     *
     * Pageable limits the number of returned results.
     */
    List<ReviewState>
    findByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            UUID userId,
            OffsetDateTime now,
            Pageable pageable
    );
}
