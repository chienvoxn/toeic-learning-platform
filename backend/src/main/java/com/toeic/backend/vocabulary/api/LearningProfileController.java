
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.LearningProfileService;
import com.toeic.backend.vocabulary.application.LearningStatisticsService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/learning-profile")
public class LearningProfileController {

    private final LearningProfileService learningProfileService;
    private final LearningStatisticsService learningStatisticsService;

    public LearningProfileController(
            LearningProfileService learningProfileService,
            LearningStatisticsService learningStatisticsService
    ) {
        this.learningProfileService = learningProfileService;
        this.learningStatisticsService = learningStatisticsService;
    }

    // TV3-04 (LP-01): Learning Profile Overview
    @GetMapping("/overview")
    public LearningOverviewResponse getOverview(
            @AuthenticationPrincipal SessionPrincipal principal
    ) {
        return learningProfileService.getOverview(
                principal.userId()
        );
    }

    // TV3-05 (LP-02): Review Statistics
    @GetMapping("/statistics")
    public LearningStatisticsResponse getStatistics(
            @AuthenticationPrincipal SessionPrincipal principal
    ) {
        return learningStatisticsService.getStatistics(
                principal.userId()
        );
    }

    // TV3-05 (LP-02): Daily Learning Progress
    @GetMapping("/progress")
    public LearningProgressResponse getProgress(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "7") int days
    ) {
        if (days < 1 || days > 90) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "days must be between 1 and 90"
            );
        }

        return learningStatisticsService.getProgress(
                principal.userId(),
                days
        );
    }
}
