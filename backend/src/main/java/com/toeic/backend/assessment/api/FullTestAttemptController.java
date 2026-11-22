
package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.FullTestSessionService;
import com.toeic.backend.assessment.application.PracticeSessionDtos;
import com.toeic.backend.identity.application.SessionPrincipal;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/full-tests")
public class FullTestAttemptController {

    private final FullTestSessionService service;

    public FullTestAttemptController(
            FullTestSessionService service) {
        this.service = service;
    }

    @PostMapping("/{testVersionId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public PracticeSessionDtos.Session start(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID testVersionId) {

        return service.start(actor.userId(), testVersionId);
    }
}
