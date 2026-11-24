
package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.FullTestCatalogService;
import com.toeic.backend.assessment.application.FullTestSessionService;
import com.toeic.backend.assessment.application.PracticeSessionDtos;
import com.toeic.backend.assessment.application.PracticeException;
import com.toeic.backend.identity.application.SessionPrincipal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/full-tests")
public class FullTestAttemptController {

    private final FullTestSessionService service;
    private final FullTestCatalogService catalog;

    public FullTestAttemptController(
            FullTestSessionService service,
            FullTestCatalogService catalog) {
        this.service = service;
        this.catalog = catalog;
    }

    @GetMapping
    public List<FullTestCatalogService.PublishedFullTest> listPublished(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam org.springframework.util.MultiValueMap<String,String> params) {
        if(!params.isEmpty())throw PracticeException.invalid();
        return catalog.listPublished(actor.userId());
    }

    @PostMapping("/{testVersionId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public PracticeSessionDtos.Session start(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID testVersionId,
            @RequestParam org.springframework.util.MultiValueMap<String,String> params,
            @RequestBody(required=false) byte[] body) {
        if(!params.isEmpty()||body!=null&&body.length>0)throw PracticeException.invalid();

        return service.start(actor.userId(), testVersionId);
    }
}
