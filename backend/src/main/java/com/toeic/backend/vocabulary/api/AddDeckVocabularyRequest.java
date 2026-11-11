
package com.toeic.backend.vocabulary.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddDeckVocabularyRequest(
        @NotNull(message = "Vocabulary ID is required")
        UUID vocabularyId
) {
}
