package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.assessment.domain.ExamDocument;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/teacher/tests")
public class ExamManagementController {
    private final ExamManagementService service;
    public ExamManagementController(ExamManagementService service){this.service=service;}
    public record Save(int revision,ExamDocument document) {}
    public record Review(int revision,String decision,String note) {}
    public record Revision(int revision) {}
    @GetMapping public Object list(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="0") int page){return service.list(p.userId(),page);}
    @GetMapping("/{id}") public Object get(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){return service.get(p.userId(),id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Object create(@AuthenticationPrincipal SessionPrincipal p,@RequestBody ExamDocument doc){return service.create(p.userId(),doc);}
    @PostMapping("/validate") public Object validate(@AuthenticationPrincipal SessionPrincipal p,@RequestBody ExamDocument doc){return service.validate(p.userId(),doc);}
    @PostMapping("/import") @ResponseStatus(HttpStatus.CREATED) public Object importDocument(@AuthenticationPrincipal SessionPrincipal p,@RequestBody ExamDocument doc){var errors=service.validate(p.userId(),doc);if(!errors.isEmpty())throw new ExamException(422,"EXAM_INVALID","Import chưa hợp lệ; chưa tạo đề.",errors);return service.create(p.userId(),doc);}
    @PutMapping("/{id}") public Object save(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody Save body){return service.save(p.userId(),id,body.revision(),body.document());}
    @PostMapping("/{id}/submit-review") public Object submit(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody Revision body){return service.submit(p.userId(),id,body.revision());}
    @PostMapping("/{id}/review") public Object review(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody Review body){return service.review(p.userId(),id,body.revision(),body.decision(),body.note());}
    @PostMapping("/{id}/publish") public Object publish(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody Revision body){return service.publish(p.userId(),id,body.revision());}
    @PostMapping("/{id}/versions") public Object version(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){return service.newVersion(p.userId(),id);}
    @PostMapping("/{id}/retire") public Object retire(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){return service.retire(p.userId(),id);}
}
