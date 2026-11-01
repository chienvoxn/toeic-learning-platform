
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.vocabulary.domain.Vocabulary;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VocabularyResponse(
    UUID vocabularyId,
    String word,
    String meaning,
    String partOfSpeech,
    String pronunciation,
    UUID pronunciationAudioResourceId,
    String example,
    String note,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static VocabularyResponse from(Vocabulary vocabulary) {
        return new VocabularyResponse(
            vocabulary.getVocabularyId(),
            vocabulary.getWord(),
            vocabulary.getMeaning(),
            vocabulary.getPartOfSpeech(),
            vocabulary.getPronunciation(),
            vocabulary.getPronunciationAudioResourceId(),
            vocabulary.getExample(),
            vocabulary.getNote(),
            vocabulary.getCreatedAt(),
            vocabulary.getUpdatedAt()
        );
    }
}
