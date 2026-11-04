package com.toeic.backend.questionbank.api;

import com.toeic.backend.questionbank.application.QuestionReadDtos;
import com.toeic.backend.questionbank.application.QuestionReadQuery;
import com.toeic.backend.questionbank.application.QuestionReadService;
import com.toeic.backend.questionbank.application.QuestionReadException;
import java.util.UUID;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionReadController {
    private final QuestionReadService questions;
    public QuestionReadController(QuestionReadService questions) { this.questions = questions; }
    @GetMapping
    public QuestionReadDtos.QuestionPage list(@RequestParam MultiValueMap<String, String> params) {
        return questions.list(QuestionReadQuery.parse(params));
    }
    @GetMapping("/{id}")
    public QuestionReadDtos.Detail detail(@PathVariable UUID id, @RequestParam MultiValueMap<String, String> params) {
        if (!params.isEmpty()) { throw QuestionReadException.invalidQuery(); }
        return questions.detail(id);
    }
}
