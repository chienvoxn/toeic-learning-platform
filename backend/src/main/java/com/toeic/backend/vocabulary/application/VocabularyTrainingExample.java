
package com.toeic.backend.vocabulary.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VocabularyTrainingExample(
        UUID vocabularyId,
        OffsetDateTime reviewedAt,
        VocabularyLearningFeatures features,
        int label
) {
}
