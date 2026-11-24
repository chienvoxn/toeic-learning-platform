package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tests")
public class FullMockCatalogController {
    private final FullMockSessionService service;
    public FullMockCatalogController(FullMockSessionService service){this.service=service;}
    @GetMapping
    public Object list(@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> params){if(!params.isEmpty())throw PracticeException.invalid();return service.catalog(actor.userId());}
}
