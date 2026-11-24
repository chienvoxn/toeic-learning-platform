package com.toeic.backend.assessment.api;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.toeic.backend.ai.application.ExamGuardService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Delta integration, using MINI-LAB fixtures; no numeric Full Test product contract is seeded. */
class DevelopAssessmentIntegrationTest extends FullMockTestSupport {
    @Autowired ExamGuardService examGuard;

    @Test void titleOnlyTeacherClientCreatesAuditedCanonicalDraftAndExplicitEditorOwnsChanges() throws Exception {
        var d=tree(call(post(TESTS).contentType(APPLICATION_JSON).content("{\"title\":\"Team UI draft\",\"description\":\"Adapter\"}"),token(TEACHER)).andExpect(status().isCreated()));
        tests.add(UUID.fromString(d.get("testId").asString()));
        assertEquals(d.get("versionId"),d.get("testVersionId"));assertEquals("DRAFT",d.get("status").asString());
        assertEquals(0,d.get("revision").asInt());assertEquals(0,d.get("document").get("sections").size());
        assertTrue(d.get("document").get("durationSeconds").isNull());
        assertEquals(1,count("SELECT count(*) FROM moderation_versions WHERE kind='tests' AND version_id=?",version(d)));
        call(post("/api/v1/teacher/test-versions/"+version(d)+"/assemble-demo"),token(TEACHER))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXPLICIT_TEST_SELECTION_REQUIRED"));
        assertEquals(0,count("SELECT count(*) FROM test_questions WHERE test_version_id=?",version(d)));
        call(get(TESTS+"/"+d.get("testId").asString()),token(OTHER)).andExpect(status().isForbidden());
        call(post(TESTS).contentType(APPLICATION_JSON).content("{\"title\":\"Denied\"}"),token(ACTOR)).andExpect(status().isForbidden());
        call(put(path(d)).contentType(APPLICATION_JSON).content("{\"revision\":0,\"document\":"+document()+"}"),token(TEACHER)).andExpect(status().isOk());
        call(put(path(d)).contentType(APPLICATION_JSON).content("{\"revision\":0,\"document\":"+document()+"}"),token(TEACHER)).andExpect(status().isConflict());
    }
    @Test void aliasStartUsesTheCanonicalSnapshotAndAiGuardRecognizesIt() throws Exception {
        var d=published();
        var a=tree(call(post("/api/v1/full-tests/"+version(d)+"/attempts"),token(ACTOR)).andExpect(status().isCreated()));safe(a);
        assertEquals(13,a.get("totalQuestions").asInt());assertEquals(600,a.get("test").get("blueprint").get("durationSeconds").asInt());
        assertEquals("FULL_MOCK",jdbc.queryForObject("SELECT attempt_type FROM attempts WHERE attempt_id=?",String.class,id(a)));
        assertFalse(examGuard.hasActiveExam(ACTOR)); // Fixture clock is in the past, guard uses the DB clock.
        jdbc.update("UPDATE attempts SET started_at=CURRENT_TIMESTAMP,expires_at=CURRENT_TIMESTAMP+interval '10 minutes' WHERE attempt_id=?",id(a));
        assertTrue(examGuard.hasActiveExam(ACTOR));
        jdbc.update("UPDATE attempts SET expires_at=CURRENT_TIMESTAMP-interval '1 second',started_at=CURRENT_TIMESTAMP-interval '11 minutes' WHERE attempt_id=?",id(a));
        assertFalse(examGuard.hasActiveExam(ACTOR));
        call(post("/api/v1/full-tests/"+version(d)+"/attempts").contentType(APPLICATION_JSON).content("{\"durationSeconds\":1}"),token(ACTOR)).andExpect(status().isBadRequest());
    }
}
