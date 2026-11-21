
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.RoleRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;
import com.toeic.backend.vocabulary.application.VocabularyLearningSessionService;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "debug=false"
})
@AutoConfigureMockMvc
class VocabularyReviewIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    VocabularyRepository vocabularies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    VocabularyLearningSessionService sessions;

    private UUID userId;
    private UUID vocabularyId;
    private String email;
    private String token;

    private final String password = "TestPass123!";

    @BeforeEach
    void setup() throws Exception {
        email = UUID.randomUUID() + "@example.com";

        User user = new User(
                email,
                encoder.encode(password),
                "SRS Integration Tester"
        );

        user.addRole(
                roles.findByName("LEARNER").orElseThrow()
        );

        user.verifyEmail();
        users.saveAndFlush(user);

        userId = user.getUserId();

        Vocabulary vocabulary = new Vocabulary(
                userId,
                "schedule-" + UUID.randomUUID(),
                "Lịch trình"
        );

        vocabularies.saveAndFlush(vocabulary);
        vocabularyId = vocabulary.getVocabularyId();

        var login = mvc.perform(
                post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of(
                                        "email", email,
                                        "password", password
                                )
                        ))
        ).andExpect(status().isOk()).andReturn();

        token = mapper.readTree(
                login.getResponse().getContentAsString()
        ).get("accessToken").asString();
    }

    @AfterEach
    void cleanup() {
        if (userId == null) {
            return;
        }

        jdbc.update("DELETE FROM vocabulary_review_requests WHERE user_id = ?", userId);
        jdbc.update("""
                DELETE FROM review_history
                WHERE review_state_id IN (
                    SELECT review_state_id
                    FROM review_states
                    WHERE user_id = ?
                )
                """, userId);

        jdbc.update(
                "DELETE FROM review_states WHERE user_id = ?",
                userId
        );

        jdbc.update("DELETE FROM vocabulary_decks WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM vocabularies WHERE created_by = ?", userId);

        jdbc.update(
                "DELETE FROM auth_sessions WHERE user_id = ?",
                userId
        );

        jdbc.update(
                "DELETE FROM email_verification_otps WHERE user_id = ?",
                userId
        );

        jdbc.update(
                "DELETE FROM user_roles WHERE user_id = ?",
                userId
        );

        jdbc.update(
                "DELETE FROM users WHERE user_id = ?",
                userId
        );
    }

    private String reviewUrl() {
        return "/api/v1/vocabularies/"
                + vocabularyId
                + "/reviews";
    }

    private void submitReview(int rating) throws Exception {
        mvc.perform(
                post(reviewUrl())
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .contentType(APPLICATION_JSON)
                        .content(
                                mapper.writeValueAsString(
                                        Map.of("rating", rating)
                                )
                        )
        ).andExpect(status().isOk());
    }

    @Test
    void deletingReviewedVocabularyUnlinksEveryDeckAndPreservesLearningHistory() throws Exception {
        UUID firstDeck = createDeckWithVocabulary();
        UUID secondDeck = createDeckWithVocabulary();

        mvc.perform(delete("/api/v1/vocabulary-decks/{deckId}/vocabularies/{vocabularyId}",
                        firstDeck, vocabularyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM deck_vocabularies WHERE vocabulary_id = ?", Integer.class, vocabularyId));

        submitReview(1);
        jdbc.update("UPDATE review_states SET next_review_at = now() WHERE vocabulary_id = ?", vocabularyId);
        mvc.perform(get("/api/v1/vocabularies/reviews/due").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(delete("/api/v1/vocabularies/{id}", vocabularyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM deck_vocabularies WHERE vocabulary_id = ?", Integer.class, vocabularyId));
        mvc.perform(get("/api/v1/vocabulary-decks/{deckId}/flashcards", secondDeck)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/vocabularies/{id}", vocabularyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post(reviewUrl()).header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"rating\":3}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/vocabularies/reviews/due").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/vocabulary/recommendations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/learning-profile/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVocabularies").value(0))
                .andExpect(jsonPath("$.reviewedVocabularies").value(0))
                .andExpect(jsonPath("$.dueForReview").value(0))
                .andExpect(jsonPath("$.totalReviews").value(1));
    }

    @Test
    void deletingCatalogCopyClearsItsSessionAndAllowsStartingAgain() throws Exception {
        String startUrl = "/api/v1/vocabulary/catalog/decks/11111111-1111-4111-8111-111111111111/start";
        UUID firstDeck = startCatalogCopy(startUrl);
        mvc.perform(put("/api/v1/vocabulary-decks/{deckId}/learning-session?expectedRevision=0", firstDeck)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/vocabulary-decks/{deckId}", firstDeck)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM vocabulary_learning_sessions WHERE deck_id = ?", Integer.class, firstDeck));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM deck_vocabularies WHERE deck_id = ?", Integer.class, firstDeck));
        assertNotEquals(firstDeck, startCatalogCopy(startUrl));
    }

    @Test
    void sessionReviewAndRetryShareOneRevisionAndHistoryEntry() throws Exception {
        String sessionUrl = sessionUrl(createDeckWithVocabulary());

        mvc.perform(sessionRating(sessionUrl, 0, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.revision").value(1))
                .andExpect(jsonPath("$.session.data.completedIds[0]").value(vocabularyId.toString()))
                .andExpect(jsonPath("$.session.data.attempts").value(1))
                .andExpect(jsonPath("$.review.intervalDays").value(1));
        mvc.perform(sessionRating(sessionUrl, 0, 3)).andExpect(status().isConflict());
        mvc.perform(put(sessionUrl).param("expectedRevision", "0").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(delete(sessionUrl).param("expectedRevision", "0").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        assertEquals(1, reviewCount());

        mvc.perform(delete(sessionUrl).param("expectedRevision", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));
        mvc.perform(sessionRating(sessionUrl, 0, 4)).andExpect(status().isConflict());
        mvc.perform(sessionRating(sessionUrl, 2, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.revision").value(3))
                .andExpect(jsonPath("$.session.data.deferredUntil." + vocabularyId).isString());
        mvc.perform(sessionRating(sessionUrl, 3, 3)).andExpect(status().isConflict());
        assertEquals(2, reviewCount());

        mvc.perform(get(sessionUrl).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.data.attempts").value(1));
    }

    @Test
    void concurrentSessionRatingsAtTheSameRevisionOnlyApplyOneReview() throws Exception {
        String sessionUrl = sessionUrl(createDeckWithVocabulary());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = pool.submit(() -> {
                start.await();
                return mvc.perform(sessionRating(sessionUrl, 0, 3)).andReturn().getResponse().getStatus();
            });
            Future<Integer> second = pool.submit(() -> {
                start.await();
                return mvc.perform(sessionRating(sessionUrl, 0, 4)).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertEquals(Set.of(200, 409),
                    Set.copyOf(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))));
        }
        assertEquals(1, reviewCount());
    }

    @Test
    void failedSnapshotWriteRollsBackSrsAndLeavesTheRevisionAvailableForRetry() throws Exception {
        UUID deckId = createDeckWithVocabulary();
        sessions.save(userId, deckId, 0, mapper.createObjectNode());
        jdbc.execute("ALTER TABLE vocabulary_learning_sessions ADD CONSTRAINT reject_test_rating "
                + "CHECK (deck_id <> '" + deckId + "' OR revision < 2)");
        try {
            assertThrows(DataIntegrityViolationException.class,
                    () -> sessions.rate(userId, deckId, 1, vocabularyId, 3));
            assertEquals(1, sessions.get(userId, deckId).revision());
            assertEquals(0, reviewCount());
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM review_states WHERE user_id = ?", Integer.class, userId));
        } finally {
            jdbc.execute("ALTER TABLE vocabulary_learning_sessions DROP CONSTRAINT reject_test_rating");
        }
        mvc.perform(sessionRating(sessionUrl(deckId), 1, 3)).andExpect(status().isOk());
        assertEquals(1, reviewCount());
    }

    private String sessionUrl(UUID deckId) {
        return "/api/v1/vocabulary-decks/" + deckId + "/learning-session";
    }

    private MockHttpServletRequestBuilder sessionRating(
            String sessionUrl, long revision, int rating
    ) throws Exception {
        return post(sessionUrl + "/reviews").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(mapper.writeValueAsString(
                        Map.of("vocabularyId", vocabularyId, "expectedRevision", revision, "rating", rating)));
    }

    private int reviewCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM review_history h JOIN review_states s USING (review_state_id)
                WHERE s.user_id = ?
                """, Integer.class, userId);
    }

    private UUID createDeckWithVocabulary() throws Exception {
        var result = mvc.perform(post("/api/v1/vocabulary-decks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Integration deck\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID deckId = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString())
                .get("deckId").asString());
        mvc.perform(post("/api/v1/vocabulary-decks/{deckId}/vocabularies", deckId)
                        .header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("vocabularyId", vocabularyId))))
                .andExpect(status().isCreated());
        return deckId;
    }

    private UUID startCatalogCopy(String url) throws Exception {
        var result = mvc.perform(post(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyEnrolled").value(false)).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString())
                .get("personalDeckId").asString());
    }

    @Test
    void repeatedReviewRequestReturnsOriginalResultAndRejectsDifferentPayload() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = mvc.perform(post(reviewUrl()).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                        .content("{\"rating\":3}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var retry = mvc.perform(post(reviewUrl()).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                        .content("{\"rating\":3}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first), mapper.readTree(retry));
        mvc.perform(post(reviewUrl()).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                        .content("{\"rating\":4}")).andExpect(status().isConflict());
        assertEquals(1, reviewCount());
        submitReview(4);
        assertEquals(2, reviewCount());
    }

    @Test
    void concurrentIdenticalReviewRequestsCreateOneHistoryEntry() throws Exception {
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<String> call = () -> {
                start.await();
                return mvc.perform(post(reviewUrl()).header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                                .content("{\"rating\":3}"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            };
            Future<String> first = pool.submit(call);
            Future<String> second = pool.submit(call);
            start.countDown();
            assertEquals(mapper.readTree(first.get(10, TimeUnit.SECONDS)),
                    mapper.readTree(second.get(10, TimeUnit.SECONDS)));
        }
        assertEquals(1, reviewCount());
    }

    // 1. Authentication

    @Test
    void unauthenticatedRequestShouldReturn401()
            throws Exception {

        mvc.perform(
                post(reviewUrl())
                        .contentType(APPLICATION_JSON)
                        .content("{\"rating\":3}")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void vocabularyApisRequireLearnerVocabularyPermission() throws Exception {
        String unprivilegedEmail = UUID.randomUUID() + "@example.com";
        User unprivileged = new User(
                unprivilegedEmail,
                encoder.encode(password),
                "Unprivileged vocabulary tester"
        );
        unprivileged.verifyEmail();
        users.saveAndFlush(unprivileged);

        try {
            var login = mvc.perform(post("/api/v1/auth/login")
                            .contentType(APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "email", unprivilegedEmail,
                                    "password", password))))
                    .andExpect(status().isOk())
                    .andReturn();
            String unprivilegedToken = mapper.readTree(login.getResponse().getContentAsString())
                    .get("accessToken").asString();

            mvc.perform(get("/api/v1/vocabularies")
                            .header("Authorization", "Bearer " + unprivilegedToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(get("/api/v1/learning-profile/overview")
                            .header("Authorization", "Bearer " + unprivilegedToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        } finally {
            jdbc.update("DELETE FROM auth_sessions WHERE user_id = ?", unprivileged.getUserId());
            jdbc.update("DELETE FROM users WHERE user_id = ?", unprivileged.getUserId());
        }

        mvc.perform(get("/api/v1/vocabularies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // 2. Rating validation

    @Test
    void invalidRatingShouldReturn400()
            throws Exception {

        mvc.perform(
                post(reviewUrl())
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .contentType(APPLICATION_JSON)
                        .content("{\"rating\":5}")
        ).andExpect(status().isBadRequest());
    }

    // 3. Unknown vocabulary

    @Test
    void unknownVocabularyShouldReturn404()
            throws Exception {

        mvc.perform(
                post("/api/v1/vocabularies/"
                        + UUID.randomUUID()
                        + "/reviews")
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .contentType(APPLICATION_JSON)
                        .content("{\"rating\":3}")
        ).andExpect(status().isNotFound());
    }

    // 4. Valid review response

    @Test
    void validReviewShouldReturn200()
            throws Exception {

        mvc.perform(
                post(reviewUrl())
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .contentType(APPLICATION_JSON)
                        .content("{\"rating\":3}")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.vocabularyId"
                ).value(vocabularyId.toString()))
                .andExpect(jsonPath(
                        "$.repetitionCount"
                ).value(1))
                .andExpect(jsonPath(
                        "$.intervalDays"
                ).value(1));
    }

    // 5. Review state persistence

    @Test
    void reviewStateShouldPersist()
            throws Exception {

        submitReview(3);

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM review_states
                WHERE user_id = ?
                  AND vocabulary_id = ?
                """,
                Integer.class,
                userId,
                vocabularyId
        );

        assertEquals(1, count);
    }

    // 6. Review history persistence

    @Test
    void reviewHistoryShouldPersist()
            throws Exception {

        submitReview(3);

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM review_history h
                JOIN review_states s
                  ON h.review_state_id = s.review_state_id
                WHERE s.user_id = ?
                  AND s.vocabulary_id = ?
                """,
                Integer.class,
                userId,
                vocabularyId
        );

        assertEquals(1, count);
    }

    // 7. Repeated review

    @Test
    void repeatedReviewShouldUpdateStateAndAddHistory()
            throws Exception {

        submitReview(3);
        submitReview(3);

        Integer stateCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM review_states
                WHERE user_id = ?
                  AND vocabulary_id = ?
                """,
                Integer.class,
                userId,
                vocabularyId
        );

        Integer historyCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM review_history h
                JOIN review_states s
                  ON h.review_state_id = s.review_state_id
                WHERE s.user_id = ?
                  AND s.vocabulary_id = ?
                """,
                Integer.class,
                userId,
                vocabularyId
        );

        assertEquals(1, stateCount);
        assertEquals(2, historyCount);
    }

    // 8. Due reviews

    @Test
    void dueReviewsShouldReturn200()
            throws Exception {

        submitReview(1);

        mvc.perform(
                get("/api/v1/vocabularies/reviews/due")
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        ).andExpect(status().isOk());
    }

    // 9. Statistics

    @Test
    void statisticsShouldReturn200()
            throws Exception {

        submitReview(3);

        mvc.perform(
                get("/api/v1/vocabularies/reviews/statistics")
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.totalVocabulary"
                ).value(1));
    }

    // 10. Review history endpoint

    @Test
    void historyEndpointShouldReturnReview()
            throws Exception {

        submitReview(3);

        mvc.perform(
                get("/api/v1/vocabularies/"
                        + vocabularyId
                        + "/reviews/history")
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[0].rating").value(3)
                );
    }

    // 11. Concurrent reviews

    @Test
    void concurrentReviewsShouldNotLoseUpdates()
            throws Exception {

        int concurrentRequests = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(concurrentRequests);

        CountDownLatch ready =
                new CountDownLatch(concurrentRequests);

        CountDownLatch start =
                new CountDownLatch(1);

        try {
            Callable<Integer> task = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start"
                    );
                }

                return mvc.perform(
                        post(reviewUrl())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(APPLICATION_JSON)
                                .content("{\"rating\":3}")
                ).andReturn().getResponse().getStatus();
            };

            Future<Integer> first =
                    executor.submit(task);

            Future<Integer> second =
                    executor.submit(task);

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both requests should be ready"
            );

            start.countDown();

            assertEquals(
                    200,
                    first.get(20, TimeUnit.SECONDS)
            );

            assertEquals(
                    200,
                    second.get(20, TimeUnit.SECONDS)
            );

            Integer stateCount = jdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM review_states
                    WHERE user_id = ?
                      AND vocabulary_id = ?
                    """,
                    Integer.class,
                    userId,
                    vocabularyId
            );

            Integer repetitionCount = jdbc.queryForObject(
                    """
                    SELECT repetition_count
                    FROM review_states
                    WHERE user_id = ?
                      AND vocabulary_id = ?
                    """,
                    Integer.class,
                    userId,
                    vocabularyId
            );

            Integer historyCount = jdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM review_history h
                    JOIN review_states s
                      ON h.review_state_id = s.review_state_id
                    WHERE s.user_id = ?
                      AND s.vocabulary_id = ?
                    """,
                    Integer.class,
                    userId,
                    vocabularyId
            );

            assertEquals(1, stateCount);
            assertEquals(2, repetitionCount);
            assertEquals(2, historyCount);

        } finally {
            start.countDown();

            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(
                            10,
                            TimeUnit.SECONDS
                    ),
                    "Executor should terminate"
            );
        }
    }
}
