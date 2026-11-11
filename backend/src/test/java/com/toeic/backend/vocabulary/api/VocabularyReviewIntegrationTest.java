
package com.toeic.backend.vocabulary.api;

import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.RoleRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;
import com.toeic.backend.vocabulary.domain.Vocabulary;
import com.toeic.backend.vocabulary.infrastructure.VocabularyRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;
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

        if (vocabularyId != null) {
            jdbc.update(
                    "DELETE FROM vocabularies WHERE vocabulary_id = ?",
                    vocabularyId
            );
        }

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
