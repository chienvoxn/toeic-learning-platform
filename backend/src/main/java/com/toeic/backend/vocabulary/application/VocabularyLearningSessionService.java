package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.ReviewStateResponse;
import com.toeic.backend.vocabulary.infrastructure.DeckVocabularyRepository;
import com.toeic.backend.vocabulary.infrastructure.ReviewStateRepository;
import com.toeic.backend.vocabulary.infrastructure.VocabularyDeckRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class VocabularyLearningSessionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final VocabularyDeckRepository decks;
    private final DeckVocabularyRepository deckVocabulary;
    private final ReviewStateRepository reviewStates;
    private final VocabularyReviewService reviews;

    public VocabularyLearningSessionService(
            JdbcTemplate jdbc, ObjectMapper mapper, VocabularyDeckRepository decks,
            DeckVocabularyRepository deckVocabulary, ReviewStateRepository reviewStates,
            VocabularyReviewService reviews
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.decks = decks;
        this.deckVocabulary = deckVocabulary;
        this.reviewStates = reviewStates;
        this.reviews = reviews;
    }

    public record Snapshot(JsonNode data, long revision, OffsetDateTime updatedAt) {}

    public record ReviewResult(Snapshot session, ReviewStateResponse review) {}

    @Transactional(readOnly = true)
    public Snapshot get(UUID userId, UUID deckId) {
        if (decks.findByDeckIdAndUserId(deckId, userId).isEmpty()) {
            throw deckNotFound();
        }
        return readSnapshot(userId, deckId);
    }

    @Transactional
    public Snapshot save(UUID userId, UUID deckId, long expectedRevision, JsonNode data) {
        lockSnapshot(userId, deckId, expectedRevision);
        return writeSnapshot(userId, deckId, data);
    }

    @Transactional
    public Snapshot clear(UUID userId, UUID deckId, long expectedRevision) {
        lockSnapshot(userId, deckId, expectedRevision);
        // Retain a revision after reset so an old request cannot match a new round.
        return writeSnapshot(userId, deckId, mapper.createObjectNode());
    }

    @Transactional
    public ReviewResult rate(UUID userId, UUID deckId, long expectedRevision, UUID vocabularyId, int rating) {
        Snapshot current = lockSnapshot(userId, deckId, expectedRevision);
        var cardIds = deckVocabulary.findByIdDeckIdOrderByAddedAtDesc(deckId).stream()
                .map(link -> link.getVocabularyId())
                .toList();
        if (!cardIds.contains(vocabularyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vocabulary not found in deck");
        }

        JsonNode stored = current == null ? mapper.createObjectNode() : current.data();
        LearningRoundProgress round = new LearningRoundProgress(stored, cardIds,
                cardId -> reviewStates.findByUserIdAndVocabularyId(userId, cardId));
        if (!round.canRate(vocabularyId, OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flashcard is finished or not due in this round");
        }

        var review = reviews.submitReview(userId, vocabularyId, rating);
        round.apply(vocabularyId, review);
        Snapshot session = writeSnapshot(userId, deckId, mapper.valueToTree(round.snapshot()));
        return new ReviewResult(session, ReviewStateResponse.from(review));
    }

    private Snapshot lockSnapshot(UUID userId, UUID deckId, long expectedRevision) {
        if (expectedRevision < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid learning session revision");
        }
        try {
            jdbc.queryForObject("""
                    SELECT deck_id FROM vocabulary_decks
                    WHERE deck_id = ? AND user_id = ? FOR UPDATE
                    """, UUID.class, deckId, userId);
        } catch (EmptyResultDataAccessException exception) {
            throw deckNotFound();
        }
        Snapshot current = readSnapshot(userId, deckId);
        long revision = current == null ? 0 : current.revision();
        if (revision != expectedRevision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Learning session has changed");
        }
        return current;
    }

    private Snapshot readSnapshot(UUID userId, UUID deckId) {
        return jdbc.query("""
                SELECT session_data::text, revision, updated_at
                FROM vocabulary_learning_sessions WHERE user_id = ? AND deck_id = ?
                """, rows -> rows.next() ? new Snapshot(mapper.readTree(rows.getString(1)),
                rows.getLong(2), rows.getObject(3, OffsetDateTime.class)) : null, userId, deckId);
    }

    private Snapshot writeSnapshot(UUID userId, UUID deckId, JsonNode data) {
        if (data == null || !data.isObject() || data.toString().length() > 65536) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid learning session");
        }
        return jdbc.queryForObject("""
                INSERT INTO vocabulary_learning_sessions(user_id, deck_id, session_data)
                VALUES (?, ?, ?::jsonb)
                ON CONFLICT (user_id, deck_id) DO UPDATE SET
                    session_data = EXCLUDED.session_data,
                    revision = vocabulary_learning_sessions.revision + 1,
                    updated_at = NOW()
                RETURNING revision, updated_at
                """, (row, number) -> new Snapshot(data, row.getLong(1),
                row.getObject(2, OffsetDateTime.class)), userId, deckId, data.toString());
    }

    private static ResponseStatusException deckNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Vocabulary deck not found");
    }
}
