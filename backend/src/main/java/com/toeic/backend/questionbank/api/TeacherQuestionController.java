package com.toeic.backend.questionbank.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.questionbank.application.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherQuestionController {
    private final QuestionAuthoringService service;
    public TeacherQuestionController(QuestionAuthoringService service) { this.service=service; }
    private static boolean group(String kind) { return kind.equals("question-groups"); }
    private static void noParams(MultiValueMap<String,String> params) { if(!params.isEmpty())throw QuestionAuthoringException.invalid(); }

    @GetMapping("/{kind:questions|question-groups}")
    public Object list(@PathVariable String kind,@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> params) {
        var query=QuestionAuthoringQuery.parse(params,group(kind));
        return group(kind)?service.listGroups(actor.userId(),query):service.listQuestions(actor.userId(),query);
    }
    @GetMapping("/{kind:questions|question-groups}/{id}")
    public Object detail(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> params) {
        noParams(params);return group(kind)?service.getGroup(actor.userId(),id):service.getQuestion(actor.userId(),id);
    }
    @GetMapping("/{kind:questions|question-groups}/{id}/history")
    public Object history(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> params) {
        noParams(params);return group(kind)?service.groupHistory(actor.userId(),id):service.questionHistory(actor.userId(),id);
    }
    @PostMapping("/{kind:questions|question-groups}")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(@PathVariable String kind,@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> params,
            @RequestBody QuestionAuthoringRequest body) {
        noParams(params);return group(kind)?service.createGroup(actor.userId(),body.command(true)):service.createQuestion(actor.userId(),body.command(false));
    }
    @PatchMapping("/{kind:questions|question-groups}/{id}")
    public Object update(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> params,
            @RequestBody QuestionAuthoringRequest body) {
        noParams(params);return group(kind)?service.updateGroup(actor.userId(),id,body.command(true)):service.updateQuestion(actor.userId(),id,body.command(false));
    }
    @DeleteMapping("/{kind:questions|question-groups}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> params,
            @RequestBody(required=false) byte[] body) {
        noParams(params);if(body!=null&&body.length>0)throw QuestionAuthoringException.invalid();
        if(group(kind))service.archiveGroup(actor.userId(),id);else service.archiveQuestion(actor.userId(),id);
    }
}
