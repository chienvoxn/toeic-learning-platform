
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.vocabulary.application.LearningProfileService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-profile")
public class LearningProfileController {

    private final LearningProfileService learningProfileService;

    public LearningProfileController(
            LearningProfileService learningProfileService
    ) {
        this.learningProfileService = learningProfileService;
    }

    @GetMapping("/overview")
    public LearningOverviewResponse getOverview(
            @AuthenticationPrincipal SessionPrincipal principal
    ) {
        return learningProfileService.getOverview(
                principal.userId()
        );
    }
}
