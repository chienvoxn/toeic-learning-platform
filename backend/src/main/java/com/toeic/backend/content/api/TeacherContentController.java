package com.toeic.backend.content.api;

import com.toeic.backend.content.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/teacher/contents")
public class TeacherContentController {
    private final ContentManagementService service;
    public TeacherContentController(ContentManagementService service) { this.service = service; }

    @GetMapping
    public ContentManagementDtos.Page list(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String,String> params) {
        return service.list(actor.userId(),ContentManagementQuery.parse(params));
    }
    @GetMapping("/{id}")
    public ContentManagementDtos.Detail detail(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id,
            @RequestParam MultiValueMap<String,String> params) {
        noParameters(params);
        return service.get(actor.userId(),id);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContentManagementDtos.Detail create(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String,String> params, @RequestBody ContentEditRequest body) {
        noParameters(params);
        return service.create(actor.userId(),body.command());
    }
    @PatchMapping("/{id}")
    public ContentManagementDtos.Detail update(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id,
            @RequestParam MultiValueMap<String,String> params, @RequestBody ContentEditRequest body) {
        noParameters(params);
        return service.update(actor.userId(),id,body.command());
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id,
            @RequestParam MultiValueMap<String,String> params, @RequestBody(required=false) byte[] body) {
        noParameters(params);
        if (body != null && body.length > 0) { throw ContentException.invalidPayload(); }
        service.archive(actor.userId(),id);
    }
    private static void noParameters(MultiValueMap<String,String> params) {
        if (!params.isEmpty()) { throw ContentException.invalidQuery(); }
    }
}
