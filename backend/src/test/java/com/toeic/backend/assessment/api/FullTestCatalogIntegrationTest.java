package com.toeic.backend.assessment.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Retains develop catalog/auth/eligibility checks against the shared publication engine. */
class FullTestCatalogIntegrationTest extends FullMockTestSupport {
    @Test void learnerSeesOnlyStartablePublishedFullTests() throws Exception {
        var d=published();String learner=token(ACTOR);
        var canonical=tree(call(get("/api/v1/tests"),learner).andExpect(status().isOk()));assertEquals(1,canonical.size());
        var alias=tree(call(get("/api/v1/full-tests"),learner).andExpect(status().isOk()));
        assertEquals(1,alias.size());assertEquals(version(d).toString(),alias.get(0).get("testVersionId").asString());
        assertEquals(13,alias.get(0).get("totalQuestions").asInt());assertEquals(600,alias.get(0).get("durationSeconds").asInt());
        assertEquals(canonical.get(0).get("blueprintCode"),alias.get(0).get("blueprintCode"));
        jdbc.update("UPDATE question_versions SET status='ARCHIVED',archived_at=CURRENT_TIMESTAMP WHERE question_version_id=?",v(1));
        call(get("/api/v1/full-tests"),learner).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
    @Test void revokedMediaAndRetiredVersionCannotBeAdvertisedAsStartable() throws Exception {
        var d=published();String learner=token(ACTOR);
        jdbc.update("UPDATE media_reuse_grants SET revoked_at=CURRENT_TIMESTAMP,revoked_by=? WHERE user_id=?",TEACHER,ADMIN);
        call(get("/api/v1/tests"),learner).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        call(post(API).contentType("application/json").content(startBody(d)),learner).andExpect(status().isConflict());
        command(d,"retire",ADMIN);
        call(get("/api/v1/full-tests"),learner).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
    @Test void missingBlueprintOrPublicationEvidenceIsNotAStartablePreset() throws Exception {
        var d=published();jdbc.update("UPDATE test_versions SET blueprint_snapshot=NULL WHERE test_version_id=?",version(d));
        call(get("/api/v1/full-tests"),token(ACTOR)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
    @Test void catalogRequiresAuthenticationAndLearnerPermission() throws Exception {
        call(get("/api/v1/full-tests"),null).andExpect(status().isUnauthorized());
        call(get("/api/v1/full-tests"),token(TEACHER)).andExpect(status().isForbidden());
        call(get("/api/v1/tests"),token(TEACHER)).andExpect(status().isForbidden());
    }
}
