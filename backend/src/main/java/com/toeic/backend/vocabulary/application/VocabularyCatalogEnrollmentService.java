package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.CatalogEnrollmentResponse;
import com.toeic.backend.vocabulary.domain.*;
import com.toeic.backend.vocabulary.infrastructure.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VocabularyCatalogEnrollmentService {

    private final SystemVocabularyDeckRepository systemDecks;
    private final SystemVocabularyRepository systemVocabularies;
    private final VocabularyCatalogEnrollmentRepository enrollments;
    private final VocabularyDeckRepository personalDecks;
    private final VocabularyRepository personalVocabularies;
    private final DeckVocabularyRepository deckVocabularies;
    private final JdbcTemplate jdbcTemplate;

    public VocabularyCatalogEnrollmentService(
            SystemVocabularyDeckRepository systemDecks,
            SystemVocabularyRepository systemVocabularies,
            VocabularyCatalogEnrollmentRepository enrollments,
            VocabularyDeckRepository personalDecks,
            VocabularyRepository personalVocabularies,
            DeckVocabularyRepository deckVocabularies,
            JdbcTemplate jdbcTemplate
    ) {
        this.systemDecks = systemDecks;
        this.systemVocabularies = systemVocabularies;
        this.enrollments = enrollments;
        this.personalDecks = personalDecks;
        this.personalVocabularies = personalVocabularies;
        this.deckVocabularies = deckVocabularies;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CatalogEnrollmentResponse start(UUID userId, UUID systemDeckId) {
        // Serialize enrollments for this user/deck pair.
        // Hash collisions only cause additional waiting, not incorrect data.
        String lockKey = "vocabulary-catalog:" + userId + ":" + systemDeckId;

        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, lockKey);
                statement.execute();
            }
            return null;
        });

        SystemVocabularyDeck systemDeck = systemDecks
                .findByDeckIdAndPublishedTrue(systemDeckId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Catalog deck not found"));

        var existing = enrollments.findByUserIdAndSystemDeckId(
                userId, systemDeckId);

        if (existing.isPresent()) {
            UUID personalDeckId = existing.get().getPersonalDeckId();
            int count = deckVocabularies
                    .findByIdDeckIdOrderByAddedAtDesc(personalDeckId)
                    .size();

            return new CatalogEnrollmentResponse(
                    systemDeckId, personalDeckId, true, count);
        }

        List<SystemVocabulary> words =
                systemVocabularies.findBySystemDeckId(systemDeckId);

        if (words.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Catalog deck has no vocabulary");
        }

        VocabularyDeck personalDeck = personalDecks.save(
                new VocabularyDeck(
                        userId,
                        systemDeck.getName(),
                        systemDeck.getDescription()
                )
        );

        List<Vocabulary> copies = new ArrayList<>();

        for (SystemVocabulary word : words) {
            Vocabulary copy = new Vocabulary(
                    userId, word.getWord(), word.getMeaning());

            copy.setPartOfSpeech(word.getPartOfSpeech());
            copy.setPronunciation(word.getPronunciation());
            copy.setExample(word.getExample());
            copy.setNote(word.getNote());

            copies.add(copy);
        }

        personalVocabularies.saveAll(copies);

        List<DeckVocabulary> links = new ArrayList<>();

        for (Vocabulary copy : copies) {
            links.add(new DeckVocabulary(
                    personalDeck.getDeckId(),
                    copy.getVocabularyId()
            ));
        }

        deckVocabularies.saveAll(links);

        enrollments.save(new VocabularyCatalogEnrollment(
                userId, systemDeckId, personalDeck.getDeckId()
        ));

        return new CatalogEnrollmentResponse(
                systemDeckId,
                personalDeck.getDeckId(),
                false,
                copies.size()
        );
    }
}
