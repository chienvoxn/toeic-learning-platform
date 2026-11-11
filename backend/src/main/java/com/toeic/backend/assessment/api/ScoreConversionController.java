package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.ScoreConversionService;
import com.toeic.backend.identity.application.SessionPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/teacher/score-conversions")
public class ScoreConversionController {
    private final ScoreConversionService service;
    public ScoreConversionController(ScoreConversionService service){this.service=service;}
    @GetMapping public Object list(){return service.list();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Object create(@AuthenticationPrincipal SessionPrincipal p,@RequestBody ScoreConversionService.Table table){return service.create(p.userId(),table);}
}
