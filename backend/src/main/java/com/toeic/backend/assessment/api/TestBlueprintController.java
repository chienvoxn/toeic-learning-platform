package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.assessment.domain.TestBlueprintSnapshot;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/teacher/blueprints")
public class TestBlueprintController {
    private final TestBlueprintService service;
    public TestBlueprintController(TestBlueprintService service){this.service=service;}
    private static void empty(MultiValueMap<String,String> p){if(!p.isEmpty())throw ExamDraftException.invalid();}
    @GetMapping public Object list(@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> p){empty(p);return service.list(actor.userId());}
    @PostMapping(consumes="application/json") @ResponseStatus(HttpStatus.CREATED)
    public Object create(@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> p,@RequestBody String body){
        empty(p);var n=ExamDraftRequest.tree(body);ExamDraftRequest.fields(n,"code","version","name","durationSeconds","parts");
        var parts=new ArrayList<TestBlueprintSnapshot.Part>();
        for(var part:ExamDraftRequest.array(n.get("parts"))){
            ExamDraftRequest.fields(part,"partNumber","questionCount","groupCount","minGroupSize","maxGroupSize","requireAudio","requireImage");
            parts.add(new TestBlueprintSnapshot.Part(ExamDraftRequest.integer(part.get("partNumber")),ExamDraftRequest.integer(part.get("questionCount")),
                    number(part.get("groupCount")),number(part.get("minGroupSize")),number(part.get("maxGroupSize")),bool(part.get("requireAudio")),bool(part.get("requireImage"))));
        }
        return service.create(actor.userId(),new TestBlueprintSnapshot(UUID.randomUUID(),ExamDraftRequest.text(n.get("code"),false),ExamDraftRequest.integer(n.get("version")),ExamDraftRequest.text(n.get("name"),false),ExamDraftRequest.integer(n.get("durationSeconds")),parts));
    }
    private static Integer number(JsonNode n){return n==null||n.isNull()?null:ExamDraftRequest.integer(n);}
    private static boolean bool(JsonNode n){if(n==null||!n.isBoolean())throw ExamDraftException.invalid();return n.asBoolean();}
}
