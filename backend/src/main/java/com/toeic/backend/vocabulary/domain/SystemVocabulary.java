package com.toeic.backend.vocabulary.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "system_vocabularies")
public class SystemVocabulary {

    @Id
    @Column(name = "vocabulary_id")
    private UUID vocabularyId;

    @Column(name = "word", nullable = false)
    private String word;

    @Column(name = "meaning", nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Column(name = "part_of_speech")
    private String partOfSpeech;

    @Column(name = "pronunciation")
    private String pronunciation;

    @Column(name = "example", columnDefinition = "TEXT")
    private String example;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    protected SystemVocabulary() {}

    public UUID getVocabularyId() { return vocabularyId; }
    public String getWord() { return word; }
    public String getMeaning() { return meaning; }
    public String getPartOfSpeech() { return partOfSpeech; }
    public String getPronunciation() { return pronunciation; }
    public String getExample() { return example; }
    public String getNote() { return note; }
}
