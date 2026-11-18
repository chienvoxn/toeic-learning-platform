
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.ReviewState;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewStateRepository
        extends JpaRepository<ReviewState, UUID> {

    String ACTIVE_VOCABULARY = """
            and exists (
                select v.vocabularyId from Vocabulary v
                where v.vocabularyId = s.vocabularyId and v.createdBy = s.userId
            )
            """;

    // Find review state of a vocabulary
    Optional<ReviewState> findByUserIdAndVocabularyId(
            UUID userId,
            UUID vocabularyId
    );

    // Find vocabularies due for review
    @Query("""
            select s from ReviewState s
            where s.userId = :userId
            and s.nextReviewAt <= :now
            """ + ACTIVE_VOCABULARY)
    List<ReviewState> findByUserIdAndNextReviewAtLessThanEqual(
            UUID userId,
            OffsetDateTime now
    );

    //  Count all review states of a user
    @Query("""
            select count(s) from ReviewState s
            where s.userId = :userId
            """ + ACTIVE_VOCABULARY)
    long countByUserId(UUID userId);

    //  Count review states due for review
    @Query("""
            select count(s) from ReviewState s
            where s.userId = :userId
            and s.nextReviewAt <= :now
            """ + ACTIVE_VOCABULARY)
    long countByUserIdAndNextReviewAtLessThanEqual(
            UUID userId,
            OffsetDateTime now
    );

    //  Count reviewed vocabularies
    @Query("""
            select count(s) from ReviewState s
            where s.userId = :userId
            and s.lastReviewedAt is not null
            """ + ACTIVE_VOCABULARY)
    long countByUserIdAndLastReviewedAtIsNotNull(
            UUID userId
    );

    //  Count reviewed vocabularies due for review
    @Query("""
            select count(s) from ReviewState s
            where s.userId = :userId
            and s.lastReviewedAt is not null
            and s.nextReviewAt <= :now
            """ + ACTIVE_VOCABULARY)
    long countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(
            UUID userId,
            OffsetDateTime now
    );

    //  Count mastered vocabularies
    @Query("""
            select count(s) from ReviewState s
            where s.userId = :userId
            and s.lastReviewedAt is not null
            and s.repetitionCount >= :repetitionCount
            """ + ACTIVE_VOCABULARY)
    long countByUserIdAndLastReviewedAtIsNotNullAndRepetitionCountGreaterThanEqual(
            UUID userId,
            int repetitionCount
    );

    /**
     *  Find reviewed vocabularies due for review.
     *
     * Only includes review states that have been reviewed
     * at least once.
     *
     * Results are ordered by nextReviewAt ascending,
     * prioritizing the most overdue vocabularies.
     *
     * Pageable limits the number of returned results.
     */
    @Query("""
            select s from ReviewState s
            where s.userId = :userId
            and s.lastReviewedAt is not null
            and s.nextReviewAt <= :now
            """ + ACTIVE_VOCABULARY + " order by s.nextReviewAt, s.vocabularyId")
    List<ReviewState>
    findByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            UUID userId,
            OffsetDateTime now,
            Pageable pageable
    );

    /**
     *  Find all review states belonging to a user.
     *
     * Used to collect review histories for building
     * personalized vocabulary training datasets.
     */
    List<ReviewState> findByUserId(UUID userId);
}
