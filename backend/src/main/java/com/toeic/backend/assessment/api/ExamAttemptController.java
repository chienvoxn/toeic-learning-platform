package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ExamAttemptController {
    private final ExamCatalogService catalog;private final ExamAttemptService attempts;
    public ExamAttemptController(ExamCatalogService catalog,ExamAttemptService attempts){this.catalog=catalog;this.attempts=attempts;}
    public record Start(UUID testVersionId,UUID startKey) {}
    public record Answer(UUID questionId,String selectedOption,long clientSeq) {}
    @GetMapping("/users/me/exam-capabilities") public Object capabilities(@AuthenticationPrincipal SessionPrincipal p){return p.permissions();}
    @GetMapping("/tests") public Object tests(@RequestParam(defaultValue="0") int page){return catalog.list(page);}
    @GetMapping("/tests/{id}") public Object test(@PathVariable UUID id){return catalog.get(id);}
    @PostMapping("/exam-attempts") @ResponseStatus(HttpStatus.CREATED) public Object start(@AuthenticationPrincipal SessionPrincipal p,@RequestBody Start body){if(body.testVersionId()==null)throw ExamException.invalid("Thiếu testVersionId.");return attempts.start(p.userId(),body.testVersionId(),body.startKey());}
    @GetMapping("/exam-attempts/{id}") public Object get(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){return attempts.get(p.userId(),id);}
    @PutMapping({"/exam-attempts/{id}/answers","/attempts/{id}/answers"}) public Object save(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody Answer body){return attempts.save(p.userId(),id,body.questionId(),body.selectedOption(),body.clientSeq());}
    @PostMapping({"/exam-attempts/{id}/submit","/attempts/{id}/submit"}) public Object submit(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){return attempts.submit(p.userId(),id);}
    @GetMapping({"/exam-attempts/{id}/result","/attempts/{id}/result"}) public Object result(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){return attempts.result(p.userId(),id);}
    @GetMapping("/users/me/exam-history") public Object history(@AuthenticationPrincipal SessionPrincipal p,@RequestParam(defaultValue="0") int page){return attempts.history(p.userId(),page);}
    @GetMapping("/users/me/exam-progress") public Object progress(@AuthenticationPrincipal SessionPrincipal p){return attempts.progress(p.userId());}
}
