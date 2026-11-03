
package com.toeic.backend.vocabulary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "deck_vocabularies")
public class DeckVocabulary {

    @EmbeddedId
    private DeckVocabularyId id;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    protected DeckVocabulary() {
    }

    public DeckVocabulary(UUID deckId, UUID vocabularyId) {
        this.id = new DeckVocabularyId(deckId, vocabularyId);
    }

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public DeckVocabularyId getId() {
        return id;
    }

    public UUID getDeckId() {
        return id.getDeckId();
    }

    public UUID getVocabularyId() {
        return id.getVocabularyId();
    }

    public OffsetDateTime getAddedAt() {
        return addedAt;
    }
}
