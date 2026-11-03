
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.VocabularyReviewService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vocabularies")
public class VocabularyReviewController {

    private final VocabularyReviewService reviewService;

    public VocabularyReviewController(
            VocabularyReviewService reviewService
    ) {
        this.reviewService = reviewService;
    }

    // 1. SUBMIT REVIEW
    @PostMapping("/{id}/reviews")
    public ResponseEntity<ReviewStateResponse> submitReview(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitReviewRequest request
    ) {
        ReviewStateResponse response = ReviewStateResponse.from(
                reviewService.submitReview(
                        principal.userId(),
                        id,
                        request.rating()
                )
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    // 2. GET DUE REVIEWS
    @GetMapping("/reviews/due")
    public List<ReviewStateResponse> getDueReviews(
            @AuthenticationPrincipal SessionPrincipal principal
    ) {
        return reviewService.getDueReviews(principal.userId())
                .stream()
                .map(ReviewStateResponse::from)
                .toList();
    }

    // 3. GET REVIEW STATISTICS
    @GetMapping("/reviews/statistics")
    public VocabularyReviewService.ReviewStatistics getStatistics(
            @AuthenticationPrincipal SessionPrincipal principal
    ) {
        return reviewService.getStatistics(principal.userId());
    }

    // 4. GET REVIEW STATE
    @GetMapping("/{id}/review-state")
    public ReviewStateResponse getReviewState(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id
    ) {
        return ReviewStateResponse.from(
                reviewService.getReviewState(
                        principal.userId(),
                        id
                )
        );
    }

    // 5. GET REVIEW HISTORY
    @GetMapping("/{id}/reviews/history")
    public List<ReviewHistoryResponse> getReviewHistory(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id
    ) {
        return reviewService.getReviewHistory(
                        principal.userId(),
                        id
                )
                .stream()
                .map(ReviewHistoryResponse::from)
                .toList();
    }
}
