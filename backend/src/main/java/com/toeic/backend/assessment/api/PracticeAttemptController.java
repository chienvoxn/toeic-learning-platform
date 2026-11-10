package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/attempts")
public class PracticeAttemptController {
    private final PracticeSessionService service;
    public PracticeAttemptController(PracticeSessionService service) { this.service=service; }
    private static void noParams(MultiValueMap<String,String> params) { if(!params.isEmpty())throw PracticeException.invalid(); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PracticeSessionDtos.Session create(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String,String> params,@RequestBody StartPracticeRequest request) {
        noParams(params);return service.create(actor.userId(),request.query());
    }
    @GetMapping("/{id}")
    public PracticeSessionDtos.Session get(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id,
            @RequestParam MultiValueMap<String,String> params) {
        noParams(params);return service.get(actor.userId(),id);
    }
}
