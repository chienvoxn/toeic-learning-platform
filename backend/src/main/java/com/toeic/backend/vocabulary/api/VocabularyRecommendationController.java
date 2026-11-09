
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.VocabularyRecommendationService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vocabulary")
public class VocabularyRecommendationController {

    private final VocabularyRecommendationService recommendationService;

    public VocabularyRecommendationController(
            VocabularyRecommendationService recommendationService
    ) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendations")
    public List<VocabularyRecommendationResponse> getRecommendations(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and 100"
            );
        }

        return recommendationService.getRecommendations(
                principal.userId(),
                limit
        );
    }
}
