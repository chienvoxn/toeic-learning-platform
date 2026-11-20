package com.toeic.backend.assessment.api;

import com.sun.net.httpserver.HttpServer;
import com.toeic.backend.listening.domain.MediaObjectSpec;
import com.toeic.backend.listening.infrastructure.S3StorageProperties;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ListeningAttemptIntegrationTest extends PracticeTestSupport {
    @Autowired
    private S3StorageProperties s3Properties;

    private HttpServer mockMinio;
    private String originalEndpoint;

    @BeforeEach
    void startMockMinio() throws Exception {
        originalEndpoint = s3Properties.getEndpoint();
        mockMinio = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockMinio.createContext("/", exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                byte[] data = MediaObjectSpec.generateDemoWav(2, 400);
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        mockMinio.start();
        String endpoint = "http://127.0.0.1:" + mockMinio.getAddress().getPort();
        s3Properties.setEndpoint(endpoint);
        s3Properties.setPublicEndpoint(endpoint);
    }

    @AfterEach
    void stopMockMinio() {
        if (mockMinio != null) {
            mockMinio.stop(0);
        }
        if (originalEndpoint != null) {
            s3Properties.setEndpoint(originalEndpoint);
            s3Properties.setPublicEndpoint(originalEndpoint);
        }
        jdbc.update("DELETE FROM media_resources WHERE owner_id = ? AND resource_id NOT BETWEEN 'b3000000-0000-0000-0000-000000000001' AND 'b3000000-0000-0000-0000-000000000009'", TEACHER);
    }

    @Test
    void teacherCanUploadAudioAndImageToMinioAndPresignWithStrictValidationAndAccessControl() throws Exception {
        byte[] wavBytes = MediaObjectSpec.generateDemoWav(3, 440);
        MockMultipartFile audioFile = new MockMultipartFile("file", "part1.wav", "audio/wav", wavBytes);

        // Unauthenticated -> 401; Learner -> 403
        mvc.perform(multipart("/api/v1/teacher/media").file(audioFile).param("resourceType", "AUDIO"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/teacher/media").file(audioFile).param("resourceType", "AUDIO")
                        .header("Authorization", "Bearer " + token(ACTOR)))
                .andExpect(status().isForbidden());

        // Teacher upload -> 201 Created with pre-signed URL
        String teacherToken = token(TEACHER);
        var uploaded = tree(mvc.perform(multipart("/api/v1/teacher/media")
                        .file(audioFile)
                        .param("resourceType", "AUDIO")
                        .param("durationSeconds", "3")
                        .param("visibility", "PUBLIC")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isCreated()));

        UUID mediaId = UUID.fromString(uploaded.get("mediaId").asString());
        assertEquals("AUDIO", uploaded.get("resourceType").asString());
        assertEquals("audio/wav", uploaded.get("mimeType").asString());
        assertEquals(3, uploaded.get("durationSeconds").asInt());
        assertTrue(uploaded.get("url").asString().contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
        assertTrue(uploaded.get("url").asString().contains("X-Amz-Signature="));

        // Teacher can presign own media
        call(get("/api/v1/teacher/media/" + mediaId + "/presign"), teacherToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.url").isNotEmpty());

        // Invalid uploads -> 400 VALIDATION_ERROR
        MockMultipartFile imageFile = new MockMultipartFile("file", "part1.svg", "image/svg+xml",
                MediaObjectSpec.generateDemoSvg("Part 1"));
        mvc.perform(multipart("/api/v1/teacher/media")
                        .file(imageFile)
                        .param("resourceType", "IMAGE")
                        .param("durationSeconds", "5")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listeningAttemptHidesTranscriptWhileInProgressAndRevealsTranscriptAndPresignedMediaAfterSubmit() throws Exception {
        String learnerToken = token(ACTOR);
        // Create a Listening Section covering Parts 1, 2, 3, 4 (7 questions total)
        String sectionPayload = json.writeValueAsString(Map.of(
                "sourceType", "SECTION_SNAPSHOT",
                "section", Map.of("type", "LISTENING", "questionCount", 7, "durationMinutes", 30)
        ));
        var session = tree(call(post(API).contentType(APPLICATION_JSON).content(sectionPayload), learnerToken)
                .andExpect(status().isCreated()));
        safe(session);
        UUID attemptId = id(session);

        // 1. Fetch attempt media while IN_PROGRESS
        var mediaBeforeSubmit = tree(call(get(API + "/" + attemptId + "/media"), learnerToken)
                .andExpect(status().isOk()));
        assertEquals("IN_PROGRESS", mediaBeforeSubmit.get("status").asString());
        assertEquals(7, mediaBeforeSubmit.get("questions").size());

        String rawBefore = mediaBeforeSubmit.toString();
        // Transcript and internal storage paths must NEVER appear while IN_PROGRESS
        for (String forbidden : List.of("transcript", "PRAC_SECRET_TRANSCRIPT", "PRAC_PRIVATE_TRANSCRIPT",
                "Q03_LEGACY_TRANSCRIPT", "Q03_LEAK_STORAGE", "b3000000-0000-0000-0000-000000000003")) {
            assertFalse(rawBefore.contains(forbidden), "Must not leak while IN_PROGRESS: " + forbidden);
        }
        assertTrue(rawBefore.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));

        // Stream media content endpoint works for pinned readable asset and 404s for private/unpinned asset
        call(get(API + "/" + attemptId + "/media/b3000000-0000-0000-0000-000000000001/content"), learnerToken)
                .andExpect(status().isOk());
        call(get(API + "/" + attemptId + "/media/b3000000-0000-0000-0000-000000000003/content"), learnerToken)
                .andExpect(status().isNotFound());

        // Other user cannot access attempt media (404)
        call(get(API + "/" + attemptId + "/media"), token(OTHER))
                .andExpect(status().isNotFound());

        // 2. Submit Listening Section
        call(post(API + "/" + attemptId + "/submit"), learnerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRADED"));

        // 3. Fetch attempt media after GRADED -> transcript is now revealed!
        var mediaAfterSubmit = tree(call(get(API + "/" + attemptId + "/media"), learnerToken)
                .andExpect(status().isOk()));
        assertEquals("GRADED", mediaAfterSubmit.get("status").asString());
        String rawAfter = mediaAfterSubmit.toString();
        assertTrue(rawAfter.contains("PRAC_SECRET_TRANSCRIPT"), "Version-pinned transcript must be revealed after submit");
        // Private media and legacy media_resources.transcript must still never leak
        assertFalse(rawAfter.contains("PRAC_PRIVATE_TRANSCRIPT"));
        assertFalse(rawAfter.contains("Q03_LEGACY_TRANSCRIPT"));
        assertFalse(rawAfter.contains("Q03_LEAK_STORAGE"));
    }
}
