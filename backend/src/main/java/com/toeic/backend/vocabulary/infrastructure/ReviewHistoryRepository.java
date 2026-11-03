
package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.ReviewHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewHistoryRepository
        extends JpaRepository<ReviewHistory, UUID> {

    List<ReviewHistory> findByReviewStateIdOrderByReviewedAtDesc(
            UUID reviewStateId
    );

    long countByReviewStateId(UUID reviewStateId);
}
