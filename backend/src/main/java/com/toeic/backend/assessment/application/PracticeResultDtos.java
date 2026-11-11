package com.toeic.backend.assessment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Allowlist chỉ được dựng sau khi attempt của actor đã GRADED. accuracy là phần trăm, 2 chữ số. */
public final class PracticeResultDtos {
    private PracticeResultDtos() { }
    public record QuestionResult(int order, UUID questionVersionId, int partNumber, UUID selectedChoiceId,
            UUID correctChoiceId, boolean isCorrect, String outcome, String explanation) { }
    public record Result(UUID attemptId, String status, OffsetDateTime submittedAt, int totalQuestions,
            int correctCount, int incorrectCount, int unansweredCount, int rawScore, BigDecimal accuracy,
            int listeningCorrectCount, int readingCorrectCount, List<QuestionResult> questions,
            @JsonInclude(JsonInclude.Include.NON_NULL) SectionDtos.Configuration section,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<SectionDtos.PartResult> parts) {
        public Result { questions = List.copyOf(questions);if(parts!=null)parts=List.copyOf(parts); }
    }
}
