
package com.toeic.backend.vocabulary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DeckVocabularyId implements Serializable {

    @Column(name = "deck_id", nullable = false)
    private UUID deckId;

    @Column(name = "vocabulary_id", nullable = false)
    private UUID vocabularyId;

    protected DeckVocabularyId() {
    }

    public DeckVocabularyId(UUID deckId, UUID vocabularyId) {
        this.deckId = deckId;
        this.vocabularyId = vocabularyId;
    }

    public UUID getDeckId() {
        return deckId;
    }

    public UUID getVocabularyId() {
        return vocabularyId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof DeckVocabularyId that)) {
            return false;
        }

        return Objects.equals(deckId, that.deckId)
                && Objects.equals(vocabularyId, that.vocabularyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deckId, vocabularyId);
    }
}
