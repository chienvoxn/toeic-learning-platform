
package com.toeic.backend.vocabulary.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateVocabularyRequest(

    @NotBlank(message = "Word is required")
    @Size(max = 150)
    String word,

    @NotBlank(message = "Meaning is required")
    String meaning,

    @Size(max = 40)
    String partOfSpeech,

    @Size(max = 255)
    String pronunciation,

    UUID pronunciationAudioResourceId,

    String example,

    String note
) {
}
