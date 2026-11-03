
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.ReviewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewHistoryRepository
        extends JpaRepository<ReviewHistory, UUID> {

    List<ReviewHistory> findByReviewStateIdOrderByReviewedAtDesc(
            UUID reviewStateId
    );

    long countByReviewStateId(UUID reviewStateId);

    // LP-01: Total review attempts of the current user
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
}
