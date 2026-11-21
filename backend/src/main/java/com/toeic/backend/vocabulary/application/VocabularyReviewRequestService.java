package com.toeic.backend.vocabulary.application;

import com.toeic.backend.vocabulary.api.ReviewStateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@Service
public class VocabularyReviewRequestService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final VocabularyReviewService reviews;

    public VocabularyReviewRequestService(JdbcTemplate jdbc, ObjectMapper mapper, VocabularyReviewService reviews) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.reviews = reviews;
    }

    @Transactional
    public ReviewStateResponse submit(UUID userId, UUID vocabularyId, int rating, UUID key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null,
                "vocabulary-review:" + userId + ":" + key);
        var previous = jdbc.query(
                "SELECT vocabulary_id, rating, response::text FROM vocabulary_review_requests WHERE user_id = ? AND request_key = ?",
                (rows, row) -> {
                    if (!vocabularyId.equals(rows.getObject(1, UUID.class)) || rating != rows.getInt(2)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used");
                    }
                    return mapper.readValue(rows.getString(3), ReviewStateResponse.class);
                }, userId, key);
        if (!previous.isEmpty()) return previous.getFirst();
        var response = ReviewStateResponse.from(reviews.submitReview(userId, vocabularyId, rating));
        jdbc.update("INSERT INTO vocabulary_review_requests(user_id, request_key, vocabulary_id, rating, response) VALUES (?, ?, ?, ?, ?::jsonb)",
                userId, key, vocabularyId, rating, mapper.writeValueAsString(response));
        return response;
    }
}
