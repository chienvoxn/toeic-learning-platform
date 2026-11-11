package com.toeic.backend.vocabulary.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "system_vocabulary_decks")
public class SystemVocabularyDeck {

    @Id
    @Column(name = "deck_id")
    private UUID deckId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    protected SystemVocabularyDeck() {}

    public UUID getDeckId() { return deckId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public boolean isPublished() { return published; }
}
