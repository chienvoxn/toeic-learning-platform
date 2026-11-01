
package com.toeic.backend.vocabulary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "vocabularies")
public class Vocabulary {

    @Id
    @Column(name = "vocabulary_id", nullable = false)
    private UUID vocabularyId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "word", nullable = false, length = 150)
    private String word;

    @Column(name = "meaning", nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Column(name = "part_of_speech", length = 40)
    private String partOfSpeech;

    @Column(name = "pronunciation", length = 255)
    private String pronunciation;

    @Column(name = "pronunciation_audio_resource_id")
    private UUID pronunciationAudioResourceId;

    @Column(name = "example", columnDefinition = "TEXT")
    private String example;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Vocabulary() {
    }

    public Vocabulary(UUID createdBy, String word, String meaning) {
        this.vocabularyId = UUID.randomUUID();
        this.createdBy = createdBy;
        this.word = word;
        this.meaning = meaning;
    }

    @PrePersist
    protected void onCreate() {
        if (vocabularyId == null) {
            vocabularyId = UUID.randomUUID();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getVocabularyId() {
        return vocabularyId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public void setPartOfSpeech(String partOfSpeech) {
        this.partOfSpeech = partOfSpeech;
    }

    public String getPronunciation() {
        return pronunciation;
    }

    public void setPronunciation(String pronunciation) {
        this.pronunciation = pronunciation;
    }

    public UUID getPronunciationAudioResourceId() {
        return pronunciationAudioResourceId;
    }

    public void setPronunciationAudioResourceId(UUID id) {
        this.pronunciationAudioResourceId = id;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
