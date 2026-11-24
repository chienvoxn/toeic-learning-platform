
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.VocabularyRecommendationService;
import com.toeic.backend.system.application.SettingDefinition;
import com.toeic.backend.system.application.SystemSettingDefaults;
import org.springframework.util.MultiValueMap;

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
    private final SystemSettingDefaults defaults;

    public VocabularyRecommendationController(
            VocabularyRecommendationService recommendationService,
            SystemSettingDefaults defaults
    ) {
        this.recommendationService = recommendationService;
        this.defaults = defaults;
    }

    @GetMapping("/recommendations")
    public List<VocabularyRecommendationResponse> getRecommendationsHttp(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam MultiValueMap<String,String> query
    ) {
        return getRecommendations(principal,defaults.resolve(SettingDefinition.RECOMMENDATION_LIMIT,query.get("limit")));
    }

    public List<VocabularyRecommendationResponse> getRecommendations(
            SessionPrincipal principal,
            int limit
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
