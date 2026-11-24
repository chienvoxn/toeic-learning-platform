package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.AttemptHistoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttemptHistoryIntegrationTest extends PracticeTestSupport {
    @Autowired AttemptHistoryService history;

    @Test void ownHistoryPagesWithoutAnswersAndReadsStoredScoresAfterBankChanges() throws Exception {
        var token=token(ACTOR);
        var graded=id(create(token,List.of(5),2));
        var original=tree(call(post(API+"/"+graded+"/submit"),token).andExpect(status().isOk()));
        var progress=id(create(token,List.of(5),2));
        create(token(OTHER),List.of(5),2);
        // Deliberately change bank grading data after submit: history/result must use persisted snapshots.
        jdbc.update("update question_versions set explanation='CHANGED_BANK_NOT_RESULT' where question_version_id in(select question_version_id from attempt_answers where attempt_id=?)",graded);
        var first=tree(call(get(API+"?size=1"),token).andExpect(status().isOk()));
        var second=tree(call(get(API+"?page=1&size=1"),token).andExpect(status().isOk()));
        assertEquals(2,first.get("totalElements").asInt());assertEquals(2,first.get("totalPages").asInt());
        assertEquals(1,first.get("items").size());assertEquals(1,second.get("items").size());
        assertNotEquals(first.get("items").get(0).get("attemptId"),second.get("items").get(0).get("attemptId"));
        for(var page:List.of(first,second)) {
            safe(page);
            var item=page.get("items").get(0);
            if(item.get("attemptId").asString().equals(progress.toString())) assertFalse(item.has("score"));
            else {
                assertEquals(graded.toString(),item.get("attemptId").asString());
                assertEquals(0,item.get("score").get("rawScore").asInt());
                assertEquals(2,item.get("score").get("unansweredCount").asInt());
                assertEquals(0,item.get("score").get("incorrectCount").asInt());
            }
            assertFalse(page.toString().contains("questions"));
        }
        assertEquals(original,tree(call(get(API+"/"+graded+"/result"),token).andExpect(status().isOk())));
        assertEquals(0,tree(call(get(API+"?page=99"),token).andExpect(status().isOk())).get("items").size());
    }
    @Test void permissionsFilterBeforeCountingAndPagingAndRejectSpoofedActor() throws Exception {
        var token=token(ACTOR);create(token,List.of(5),2);
        // Valid pinned Section via the real service, not fabricated history.
        call(post(API).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""
                {"sourceType":"SECTION_SNAPSHOT","section":{"type":"READING","questionCount":6,"durationMinutes":20}}
                """),token).andExpect(status().isCreated());
        authenticate(ACTOR,"practice:manage:self");
        var practice=history.list(ACTOR,0,1);assertEquals(1,practice.totalElements());
        assertEquals("PRACTICE_SNAPSHOT",practice.items().getFirst().sourceType());
        authenticate(ACTOR,"exam:manage:self");
        var exam=history.list(ACTOR,0,1);assertEquals(1,exam.totalElements());
        assertEquals("SECTION_SNAPSHOT",exam.items().getFirst().sourceType());
        assertThrows(AccessDeniedException.class,()->history.list(OTHER,0,20));
        authenticate(ACTOR);assertThrows(AccessDeniedException.class,()->history.list(ACTOR,0,20));
    }
    @ParameterizedTest
    @ValueSource(strings={"page=-1","size=0","size=101","page=2147483648","page=1&page=2","size=", "page=1.2","actorId=other","sort=bank"})
    void invalidPaginationAndForeignFiltersAreRejected(String query) throws Exception {
        call(get(API+"?"+query),token(ACTOR)).andExpect(status().isBadRequest());
    }
    @Test void anonymousCannotReadHistory() throws Exception {
        call(get(API),null).andExpect(status().isUnauthorized());
    }
}
