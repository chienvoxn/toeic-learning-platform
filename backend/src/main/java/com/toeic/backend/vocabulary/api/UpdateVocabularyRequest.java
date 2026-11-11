
package com.toeic.backend.vocabulary.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateVocabularyRequest(

        @NotBlank(message = "Word must not be blank")
        @Size(max = 150)
        String word,

        @NotBlank(message = "Meaning must not be blank")
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
