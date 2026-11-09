
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.vocabulary.domain.Vocabulary;

import java.time.OffsetDateTime;

public record VocabularyRecommendationResponse(
        VocabularyResponse vocabulary,
        String reason,
        OffsetDateTime nextReviewAt
) {

    public static VocabularyRecommendationResponse due(
            Vocabulary vocabulary,
            OffsetDateTime nextReviewAt
    ) {
        return new VocabularyRecommendationResponse(
                VocabularyResponse.from(vocabulary),
                "DUE",
                nextReviewAt
        );
    }

    public static VocabularyRecommendationResponse newVocabulary(
            Vocabulary vocabulary
    ) {
        return new VocabularyRecommendationResponse(
                VocabularyResponse.from(vocabulary),
                "NEW",
                null
        );
    }
}
