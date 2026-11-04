
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.vocabulary.domain.VocabularyDeck;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeckResponse(
        UUID deckId,
        String name,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static DeckResponse from(VocabularyDeck deck) {
        return new DeckResponse(
                deck.getDeckId(),
                deck.getName(),
                deck.getDescription(),
                deck.getCreatedAt(),
                deck.getUpdatedAt()
        );
    }
}
