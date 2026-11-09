
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.ReviewHistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReviewHistoryRepository
        extends JpaRepository<ReviewHistory, UUID> {

    // TV3-03: Review history of a review state
    List<ReviewHistory> findByReviewStateIdOrderByReviewedAtDesc(
            UUID reviewStateId
    );

    long countByReviewStateId(UUID reviewStateId);

    // TV3-04: Total reviews by user
    @Query("""
            SELECT COUNT(h)
            FROM ReviewHistory h
            WHERE h.reviewStateId IN (
                SELECT s.reviewStateId
                FROM ReviewState s
                WHERE s.userId = :userId
            )
            """)
    long countReviewsByUserId(
            @Param("userId") UUID userId
    );

    // TV3-05: Reviews grouped by rating
    @Query("""
            SELECT h.rating, COUNT(h)
            FROM ReviewHistory h
            WHERE h.reviewStateId IN (
                SELECT s.reviewStateId
                FROM ReviewState s
                WHERE s.userId = :userId
            )
            GROUP BY h.rating
            ORDER BY h.rating
            """)
    List<Object[]> countReviewsGroupedByRating(
            @Param("userId") UUID userId
    );

    // TV3-05: Review history within a period
    @Query("""
            SELECT h
            FROM ReviewHistory h
            WHERE h.reviewStateId IN (
                SELECT s.reviewStateId
                FROM ReviewState s
                WHERE s.userId = :userId
            )
            AND h.reviewedAt >= :from
            AND h.reviewedAt < :to
            ORDER BY h.reviewedAt ASC
            """)
    List<ReviewHistory> findReviewsByUserIdAndPeriod(
            @Param("userId") UUID userId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
}
