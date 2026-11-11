package com.toeic.backend.vocabulary.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.toeic.backend.vocabulary.infrastructure.VocabularyDeckRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class VocabularyLearningSessionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final VocabularyDeckRepository decks;
    public VocabularyLearningSessionService(JdbcTemplate jdbc, ObjectMapper mapper, VocabularyDeckRepository decks) {
        this.jdbc = jdbc; this.mapper = mapper; this.decks = decks;
    }
    public record Snapshot(JsonNode data, long revision, OffsetDateTime updatedAt) {}

    private void requireOwned(UUID userId, UUID deckId) {
        if (decks.findByDeckIdAndUserId(deckId, userId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vocabulary deck not found");
    }

    @Transactional(readOnly = true)
    public Snapshot get(UUID userId, UUID deckId) {
        requireOwned(userId, deckId);
        return jdbc.query("SELECT session_data::text, revision, updated_at FROM vocabulary_learning_sessions WHERE user_id=? AND deck_id=?",
            rs -> rs.next() ? new Snapshot(parse(rs.getString(1)), rs.getLong(2), rs.getObject(3, OffsetDateTime.class)) : null,
            userId, deckId);
    }

    @Transactional
    public Snapshot save(UUID userId, UUID deckId, JsonNode data) {
        requireOwned(userId, deckId);
        if (data == null || !data.isObject() || data.toString().length() > 65536)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid learning session");
        // Serialize on the parent deck row to avoid concurrent upserts for the same user/deck.
        jdbc.queryForObject("SELECT deck_id FROM vocabulary_decks WHERE deck_id=? AND user_id=? FOR UPDATE", UUID.class, deckId, userId);
        jdbc.update("INSERT INTO vocabulary_learning_sessions(user_id,deck_id,session_data) VALUES(?,?,?::jsonb) " +
                    "ON CONFLICT(user_id,deck_id) DO UPDATE SET session_data=EXCLUDED.session_data, " +
                    "revision=vocabulary_learning_sessions.revision+1, updated_at=NOW()",
                    userId, deckId, data.toString());
        return get(userId, deckId);
    }

    @Transactional
    public void clear(UUID userId, UUID deckId) {
        requireOwned(userId, deckId);
        jdbc.update("DELETE FROM vocabulary_learning_sessions WHERE user_id=? AND deck_id=?", userId, deckId);
    }

    private JsonNode parse(String value) {
        try { return mapper.readTree(value); }
        catch (Exception ex) { throw new IllegalStateException("Invalid stored learning session", ex); }
    }
}
