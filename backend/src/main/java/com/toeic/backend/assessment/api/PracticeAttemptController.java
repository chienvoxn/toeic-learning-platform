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
    private final PracticeSubmissionService submissions;
    private final SectionSessionService sections;
    private final FullMockSessionService full;
    private final AttemptHistoryService history;
    public PracticeAttemptController(PracticeSessionService service,PracticeSubmissionService submissions,SectionSessionService sections,FullMockSessionService full,AttemptHistoryService history) {
        this.service=service;this.submissions=submissions;this.sections=sections;this.full=full;this.history=history;
    }
    private static void noParams(MultiValueMap<String,String> params) { if(!params.isEmpty())throw PracticeException.invalid(); }
    @GetMapping
    public AttemptHistoryDtos.Page history(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String,String> params) {
        if(params.keySet().stream().anyMatch(k->!java.util.Set.of("page","size").contains(k)))throw PracticeException.invalid();
        return history.list(actor.userId(),pageNumber(params,"page",0),pageNumber(params,"size",20));
    }
    private static int pageNumber(MultiValueMap<String,String> params,String name,int fallback) {
        var values=params.get(name);
        if(values==null)return fallback;
        if(values.size()!=1||!values.getFirst().matches("[0-9]{1,10}"))throw PracticeException.invalid();
        try{return Integer.parseInt(values.getFirst());}catch(NumberFormatException ex){throw PracticeException.invalid();}
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PracticeSessionDtos.Session create(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String,String> params,@RequestBody StartPracticeRequest request) {
        noParams(params);return request.isTest()?full.create(actor.userId(),request.testVersion()):request.isSection()?sections.create(actor.userId(),request.sectionQuery()):service.create(actor.userId(),request.query());
    }
    @GetMapping("/{id}")
    public PracticeSessionDtos.Session get(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id,
            @RequestParam MultiValueMap<String,String> params) {
        noParams(params);return service.get(actor.userId(),id);
    }
    @PutMapping("/{id}/answers/{questionVersionId}")
    public PracticeSessionDtos.SavedAnswer save(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id,
            @PathVariable UUID questionVersionId,@RequestParam MultiValueMap<String,String> params,
            @RequestBody SavePracticeAnswerRequest request) {
        noParams(params);return submissions.save(actor.userId(),id,questionVersionId,request.choiceId());
    }
    @PostMapping("/{id}/submit")
    public PracticeResultDtos.Result submit(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id,
            @RequestParam MultiValueMap<String,String> params,@RequestBody(required=false) byte[] body) {
        noParams(params);
        if(body!=null&&body.length>0)throw PracticeException.invalid();
        return submissions.submit(actor.userId(),id);
    }
    @GetMapping("/{id}/result")
    public PracticeResultDtos.Result result(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id,
            @RequestParam MultiValueMap<String,String> params) {
        noParams(params);return submissions.result(actor.userId(),id);
    }
}
