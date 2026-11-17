package com.toeic.backend.assessment.api;

import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** QA-02: cùng phiên thật qua Security/DB Auth → Practice/SRS/Section → lock/password revoke. */
class Sprint2CrossModuleIntegrationTest extends PracticeTestSupport {
    @AfterEach void cleanupCrossModuleData() {
        jdbc.update("DELETE FROM review_history WHERE review_state_id IN (SELECT review_state_id FROM review_states WHERE user_id=?)",ACTOR);
        jdbc.update("DELETE FROM review_states WHERE user_id=?",ACTOR);
        jdbc.update("DELETE FROM vocabularies WHERE created_by=?",ACTOR);
        jdbc.update("DELETE FROM learning_profiles WHERE user_id=?",ACTOR);
        jdbc.update("DELETE FROM audit_logs WHERE actor_user_id=?",ADMIN);
    }
    @Test void sameActorPersistsGradesAndSrsAcrossLockUnlockThenPasswordRevokesEveryModule() throws Exception {
        var actor=token(ACTOR);var other=token(OTHER);var admin=token(ADMIN);
        call(patch("/api/v1/users/me").contentType(APPLICATION_JSON).content("{\"fullName\":\"QA-02 Learner\",\"targetScore\":750}"),actor)
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetScore").value(750));
        var vocab=tree(call(post("/api/v1/vocabularies").contentType(APPLICATION_JSON).content("{\"word\":\"schedule\",\"meaning\":\"lịch trình\"}"),actor)
                .andExpect(status().isCreated())).get("vocabularyId").asString();
        call(post("/api/v1/vocabularies/"+vocab+"/reviews").contentType(APPLICATION_JSON).content("{\"rating\":3}"),actor)
                .andExpect(status().isOk()).andExpect(jsonPath("$.nextReviewAt").isNotEmpty());
        assertEquals(1,count("SELECT count(*) FROM review_history h JOIN review_states s USING(review_state_id) WHERE s.user_id=?",ACTOR));
        var attempts=new ArrayList<String>(); var persisted=new ArrayList<String>();
        for(var source:List.of("PRACTICE_SNAPSHOT","SECTION_SNAPSHOT")) {
            String body=source.equals("PRACTICE_SNAPSHOT")?payload(List.of(5),2):"{\"sourceType\":\"SECTION_SNAPSHOT\",\"section\":{\"type\":\"READING\",\"questionCount\":6,\"durationMinutes\":20}}";
            var session=tree(call(post(API).contentType(APPLICATION_JSON).content(body),actor).andExpect(status().isCreated()));safe(session);
            String attempt=session.get("attemptId").asString();attempts.add(attempt);
            var question=session.get("questions").get(0);String version=question.get("questionVersionId").asString();
            String choice=question.get("choices").get(0).get("choiceId").asString();
            String path=API+"/"+attempt+"/answers/"+version;
            call(put(path).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":\""+choice+"\"}"),actor).andExpect(status().isOk());
            call(put(path).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":null}"),actor).andExpect(status().isOk());
            call(put(path).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":\""+choice+"\"}"),actor).andExpect(status().isOk());
            safe(tree(call(get(API+"/"+attempt),actor).andExpect(status().isOk())));
            call(get(API+"/"+attempt+"/result"),actor).andExpect(status().isConflict());
            var result=tree(call(post(API+"/"+attempt+"/submit"),actor).andExpect(status().isOk()));
            assertEquals(1,result.get("rawScore").asInt());
            if(source.equals("SECTION_SNAPSHOT"))assertEquals(3,result.get("parts").size());
            persisted.add(result.toString());
            assertEquals(result,tree(call(post(API+"/"+attempt+"/submit"),actor).andExpect(status().isOk())));
            call(get(API+"/"+attempt+"/result"),other).andExpect(status().isNotFound());
            call(put(path).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":null}"),actor).andExpect(status().isConflict());
        }
        call(patch("/api/v1/admin/users/"+ACTOR+"/status").contentType(APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"),admin).andExpect(status().isOk());
        for(var attempt:attempts)call(get(API+"/"+attempt+"/result"),actor).andExpect(status().isUnauthorized());
        call(post("/api/v1/vocabularies/"+vocab+"/reviews").contentType(APPLICATION_JSON).content("{\"rating\":3}"),actor).andExpect(status().isUnauthorized());
        call(patch("/api/v1/admin/users/"+ACTOR+"/status").contentType(APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"),admin).andExpect(status().isOk());
        for(int n=0;n<attempts.size();n++)assertEquals(persisted.get(n),tree(call(get(API+"/"+attempts.get(n)+"/result"),actor).andExpect(status().isOk())).toString());
        call(post("/api/v1/users/me/password").contentType(APPLICATION_JSON).content("{\"currentPassword\":\"PracticeFixtureOnly123!\",\"newPassword\":\"QA02UpdatedPassword1!\"}"),actor).andExpect(status().isNoContent());
        for(var attempt:attempts)call(get(API+"/"+attempt),actor).andExpect(status().isUnauthorized());
        call(get("/api/v1/vocabularies/"+vocab+"/review-state"),actor).andExpect(status().isUnauthorized());
        call(get("/api/v1/users/me"),other).andExpect(status().isOk());
        assertEquals(1,count("SELECT count(*) FROM review_states WHERE user_id=?",ACTOR));
    }
}
