package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/teacher/tests")
public class TeacherTestController {
    private final ExamDraftService service;
    private final ExamWorkflowService workflow;
    public TeacherTestController(ExamDraftService service,ExamWorkflowService workflow) { this.service=service;this.workflow=workflow; }
    private static void noParams(MultiValueMap<String,String> params) { if(!params.isEmpty())throw ExamDraftException.invalid(); }
    @GetMapping("/capabilities")
    public Object capabilities(@AuthenticationPrincipal SessionPrincipal actor, @RequestParam MultiValueMap<String,String> params) {
        noParams(params);return workflow.capabilities(actor.userId());
    }
    @GetMapping("/question-units")
    public Object units(@AuthenticationPrincipal SessionPrincipal actor, @RequestParam MultiValueMap<String,String> params) {
        if(!Set.of("partNumber","page").containsAll(params.keySet()) || !params.containsKey("partNumber")
                || params.values().stream().anyMatch(v->v.size()!=1))throw ExamDraftException.invalid();
        try {
            String part=params.getFirst("partNumber"),page=params.getFirst("page");
            if(part==null || !part.matches("[1-7]") || page!=null && !page.matches("0|[1-9][0-9]*"))throw ExamDraftException.invalid();
            return service.units(actor.userId(),Integer.parseInt(part),page==null?0:Integer.parseInt(page));
        } catch(NumberFormatException e) { throw ExamDraftException.invalid(); }
    }
    @GetMapping
    public Object list(@AuthenticationPrincipal SessionPrincipal actor, @RequestParam MultiValueMap<String,String> params) {
        if(!Set.of("page").containsAll(params.keySet()) || params.values().stream().anyMatch(v->v.size()!=1))throw ExamDraftException.invalid();
        int page=0;
        try { if(params.containsKey("page")) { var raw=params.getFirst("page"); if(raw==null || !raw.matches("0|[1-9][0-9]*"))throw ExamDraftException.invalid(); page=Integer.parseInt(raw); } }
        catch(NumberFormatException e) { throw ExamDraftException.invalid(); }
        return workflow.list(actor.userId(),page);
    }
    @GetMapping({"/{testId}","/{testId}/versions/{versionId}"})
    public Object read(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID testId,
            @PathVariable(required=false) UUID versionId, @RequestParam MultiValueMap<String,String> params) {
        noParams(params); return workflow.read(actor.userId(),testId,versionId);
    }
    @PostMapping(consumes="application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(@AuthenticationPrincipal SessionPrincipal actor, @RequestParam MultiValueMap<String,String> params, @RequestBody String body) {
        noParams(params); var draft=service.create(actor.userId(),ExamDraftRequest.parse(body,false).document());return workflow.read(actor.userId(),draft.testId(),draft.versionId());
    }
    @PutMapping(path="/{testId}/versions/{versionId}",consumes="application/json")
    public Object save(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID testId, @PathVariable UUID versionId,
            @RequestParam MultiValueMap<String,String> params, @RequestBody String body) {
        noParams(params); var edit=ExamDraftRequest.parse(body,true);
        service.update(actor.userId(),testId,versionId,edit.revision(),edit.document());return workflow.read(actor.userId(),testId,versionId);
    }
    @GetMapping("/{testId}/versions")
    public Object versions(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID testId,@RequestParam MultiValueMap<String,String> params){noParams(params);return workflow.versions(actor.userId(),testId);}
    @PostMapping("/{testId}/versions/{versionId}/{command:submit-review|approve|return|revoke|publish}")
    public Object transition(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID testId,@PathVariable UUID versionId,@PathVariable String command,@RequestParam MultiValueMap<String,String> params,@RequestBody com.toeic.backend.system.api.ModerationRequest body){
        noParams(params);String action=command.equals("submit-review")?"submit":command;var value=body.validate(action,false);
        return workflow.transition(actor.userId(),testId,versionId,action,value.expectedRevision(),value.reason());
    }
    @PostMapping("/{testId}/versions/{versionId}/{command:fork|retire|unpublish}")
    public Object versionCommand(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID testId,@PathVariable UUID versionId,@PathVariable String command,@RequestParam MultiValueMap<String,String> params,@RequestBody String body){
        noParams(params);var n=ExamDraftRequest.tree(body);ExamDraftRequest.fields(n,"expectedRevision");long revision=ExamDraftRequest.integer(n.get("expectedRevision"));
        return command.equals("fork")?workflow.fork(actor.userId(),testId,versionId,revision):workflow.retire(actor.userId(),testId,versionId,revision);
    }
    @PutMapping("/{testId}/versions/{versionId}/blueprint")
    public Object blueprint(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID testId,@PathVariable UUID versionId,@RequestParam MultiValueMap<String,String> params,@RequestBody String body){
        noParams(params);var n=ExamDraftRequest.tree(body);ExamDraftRequest.fields(n,"expectedRevision","blueprintId");return workflow.blueprint(actor.userId(),testId,versionId,ExamDraftRequest.integer(n.get("expectedRevision")),ExamDraftRequest.uuid(n.get("blueprintId"),false));
    }
}
