
package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.domain.ReviewHistory;
import com.toeic.backend.vocabulary.domain.ReviewState;
import com.toeic.backend.vocabulary.infrastructure.ReviewHistoryRepository;
import com.toeic.backend.vocabulary.infrastructure.ReviewStateRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class VocabularyReviewService {

    private final VocabularyRepository vocabularyRepository;
    private final ReviewStateRepository reviewStateRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final SrsScheduler scheduler;

    public VocabularyReviewService(
            VocabularyRepository vocabularyRepository,
            ReviewStateRepository reviewStateRepository,
            ReviewHistoryRepository reviewHistoryRepository,
            SrsScheduler scheduler
    ) {
        this.vocabularyRepository = vocabularyRepository;
        this.reviewStateRepository = reviewStateRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.scheduler = scheduler;
    }

    // 1. SUBMIT REVIEW
    @Transactional
    public ReviewState submitReview(
            UUID currentUserId,
            UUID vocabularyId,
            int rating
    ) {
        if (rating < 1 || rating > 4) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rating must be between 1 and 4"
            );
        }

        // Lock the vocabulary row before reading ReviewState.
        // Concurrent reviews of the same vocabulary are serialized.
        vocabularyRepository.findOwnedByIdForUpdate(
                vocabularyId,
                currentUserId
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Vocabulary not found"
        ));

        ReviewState state = reviewStateRepository
                .findByUserIdAndVocabularyId(
                        currentUserId,
                        vocabularyId
                )
                .orElseGet(() -> new ReviewState(
                        currentUserId,
                        vocabularyId
                ));

        OffsetDateTime now =
                OffsetDateTime.now(ZoneOffset.UTC);

        SrsScheduler.ScheduleResult result =
                scheduler.calculate(state, rating, now);

        state.updateSchedule(
                result.repetitionCount(),
                result.newInterval(),
                result.easeFactor(),
                now,
                result.nextReviewAt()
        );

        ReviewState savedState =
                reviewStateRepository.save(state);

        ReviewHistory history = new ReviewHistory(
                savedState.getReviewStateId(),
                rating,
                result.previousInterval(),
                result.newInterval()
        );

        reviewHistoryRepository.save(history);

        return savedState;
    }

    // 2. GET REVIEW STATE
    @Transactional(readOnly = true)
    public ReviewState getReviewState(
            UUID currentUserId,
            UUID vocabularyId
    ) {
        checkOwnedVocabulary(currentUserId, vocabularyId);

        return reviewStateRepository
                .findByUserIdAndVocabularyId(
                        currentUserId,
                        vocabularyId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Review state not found"
                ));
    }

    // 3. GET DUE REVIEWS
    @Transactional(readOnly = true)
    public List<ReviewState> getDueReviews(
            UUID currentUserId
    ) {
        return reviewStateRepository
                .findByUserIdAndNextReviewAtLessThanEqual(
                        currentUserId,
                        OffsetDateTime.now(ZoneOffset.UTC)
                );
    }

    // 4. GET STATISTICS
    @Transactional(readOnly = true)
    public ReviewStatistics getStatistics(
            UUID currentUserId
    ) {
        OffsetDateTime now =
                OffsetDateTime.now(ZoneOffset.UTC);

        long total = reviewStateRepository
                .countByUserId(currentUserId);

        long due = reviewStateRepository
                .countByUserIdAndNextReviewAtLessThanEqual(
                        currentUserId,
                        now
                );

        return new ReviewStatistics(total, due);
    }

    // 5. GET REVIEW HISTORY
    @Transactional(readOnly = true)
    public List<ReviewHistory> getReviewHistory(
            UUID currentUserId,
            UUID vocabularyId
    ) {
        ReviewState state = getReviewState(
                currentUserId,
                vocabularyId
        );

        return reviewHistoryRepository
                .findByReviewStateIdOrderByReviewedAtDesc(
                        state.getReviewStateId()
                );
    }

    // 6. CHECK OWNERSHIP
    private void checkOwnedVocabulary(
            UUID currentUserId,
            UUID vocabularyId
    ) {
        vocabularyRepository
                .findById(vocabularyId)
                .filter(vocabulary ->
                        vocabulary.getCreatedBy()
                                .equals(currentUserId)
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vocabulary not found"
                ));
    }

    public record ReviewStatistics(
            long totalVocabulary,
            long dueVocabulary
    ) {
    }
}
