package com.toeic.backend.vocabulary.api;

import com.toeic.backend.vocabulary.domain.SystemVocabulary;
import java.util.UUID;

public record CatalogVocabularyResponse(
        UUID vocabularyId,
        String word,
        String meaning,
        String partOfSpeech,
        String pronunciation,
        String example,
        String note
) {
    public static CatalogVocabularyResponse from(SystemVocabulary vocabulary) {
        return new CatalogVocabularyResponse(
                vocabulary.getVocabularyId(),
                vocabulary.getWord(),
                vocabulary.getMeaning(),
                vocabulary.getPartOfSpeech(),
                vocabulary.getPronunciation(),
                vocabulary.getExample(),
                vocabulary.getNote()
        );
    }
}
