package com.toeic.backend.assessment.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AttemptHistoryDtos {
    private AttemptHistoryDtos() { }
    public record Page(List<Item> items, int page, int size, long totalElements, long totalPages) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(UUID attemptId, String sourceType, String attemptType, String status,
            OffsetDateTime startedAt, OffsetDateTime expiresAt, OffsetDateTime submittedAt,
            int totalQuestions, Score score) { }
    public record Score(int correctCount, int incorrectCount, int unansweredCount, int rawScore, BigDecimal accuracy) { }
}
