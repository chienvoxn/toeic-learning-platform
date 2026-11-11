
package com.toeic.backend.vocabulary.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDeckRequest(

        @NotBlank(message = "Deck name is required")
        @Size(max = 150, message = "Deck name must not exceed 150 characters")
        String name,

        String description

) {
}
