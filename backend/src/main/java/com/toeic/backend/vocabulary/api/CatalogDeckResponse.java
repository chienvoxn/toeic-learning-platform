package com.toeic.backend.vocabulary.api;

import com.toeic.backend.vocabulary.domain.SystemVocabularyDeck;
import java.util.UUID;

public record CatalogDeckResponse(
        UUID deckId,
        String name,
        String description,
        String category
) {
    public static CatalogDeckResponse from(SystemVocabularyDeck deck) {
        return new CatalogDeckResponse(
                deck.getDeckId(),
                deck.getName(),
                deck.getDescription(),
                deck.getCategory()
        );
    }
}
