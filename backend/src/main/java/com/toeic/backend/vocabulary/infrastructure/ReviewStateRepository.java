
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.ReviewState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewStateRepository
        extends JpaRepository<ReviewState, UUID> {

    Optional<ReviewState> findByUserIdAndVocabularyId(
            UUID userId,
            UUID vocabularyId
    );

    List<ReviewState> findByUserIdAndNextReviewAtLessThanEqual(
            UUID userId,
            OffsetDateTime now
    );

    long countByUserId(UUID userId);

    long countByUserIdAndNextReviewAtLessThanEqual(
            UUID userId,
            OffsetDateTime now
    );

    // LP-01: Vocabularies reviewed at least once
    long countByUserIdAndLastReviewedAtIsNotNull(
            UUID userId
    );

    // LP-01: Reviewed vocabularies due for review
    long countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(
            UUID userId,
            OffsetDateTime now
    );

    // LP-01: Vocabularies reaching mastery threshold
    long countByUserIdAndLastReviewedAtIsNotNullAndRepetitionCountGreaterThanEqual(
            UUID userId,
            int repetitionCount
    );
}
