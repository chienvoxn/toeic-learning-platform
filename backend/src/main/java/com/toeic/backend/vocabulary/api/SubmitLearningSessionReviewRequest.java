package com.toeic.backend.vocabulary.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitLearningSessionReviewRequest(
        @NotNull @Min(0) Long expectedRevision,
        @NotNull UUID vocabularyId,
        @NotNull @Min(1) @Max(4) Integer rating
) {}
