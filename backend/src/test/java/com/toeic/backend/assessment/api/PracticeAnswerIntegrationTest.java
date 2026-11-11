package com.toeic.backend.assessment.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import com.toeic.backend.assessment.application.PracticeSubmissionService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PracticeAnswerIntegrationTest extends PracticeTestSupport {
    @Autowired PracticeSubmissionService submissions;
    private UUID choice(UUID version, String label) {
        return jdbc.queryForObject("select choice_id from question_choices where question_version_id=? and choice_label=?", UUID.class, version, label);
    }
    private String answer(UUID choice) { return json.writeValueAsString(Collections.singletonMap("selectedChoiceId", choice)); }
    private String path(UUID attempt, UUID version) { return API+"/"+attempt+"/answers/"+version; }

    @Test void saveReplaceClearReloadWithoutAnswerKeyLeak() throws Exception {
        var token=token(ACTOR);var session=create(token,List.of(2),1);var attempt=id(session);
        for(var selected:Arrays.asList(choice(v(3),"A"),choice(v(3),"B"),null)) {
            var saved=tree(call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(answer(selected)),token).andExpect(status().isOk()));
            safe(saved);assertEquals(v(3).toString(),saved.get("questionVersionId").asString());
            var reloaded=read(token,attempt);safe(reloaded);assertEquals(1,reloaded.get("answers").size());
            assertEquals(saved.toString(),reloaded.get("answers").get(0).toString());
            assertEquals(1,count("select count(*) from attempt_answers where attempt_id=? and is_correct is null",attempt));
        }
        assertEquals(1,count("select count(*) from attempt_answers where attempt_id=? and selected_choice_id is null and answered_at is null",attempt));
    }
    @Test void rejectsQuestionOutsideSnapshotAndChoiceOfOtherVersion() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));
        call(put(path(attempt,v(1))).contentType(APPLICATION_JSON).content(answer(choice(v(1),"A"))),token)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ATTEMPT_QUESTION_NOT_FOUND"));
        for(var selected:List.of(choice(v(1),"A"),UUID.randomUUID())) {
            call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(answer(selected)),token)
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CHOICE_VERSION_MISMATCH"));
        }
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=?",attempt));
    }
    @Test void expiryBoundaryAndStatusAreCheckedBeforeWrite() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));
        jdbc.update("update attempts set started_at=?,expires_at=? where attempt_id=?",OffsetDateTime.ofInstant(NOW.minusSeconds(86400),ZoneOffset.UTC),OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC),attempt);
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(answer(choice(v(3),"A"))),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));
        jdbc.update("update attempts set status='SUBMITTED' where attempt_id=?",attempt);
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(answer(null)),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_NOT_EDITABLE"));
        assertTrue(read(token,attempt).get("expired").asBoolean());
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=?",attempt));
    }
    @Test void ownershipPermissionAuthenticationAndSourceAreEnforced() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));var request=answer(choice(v(3),"A"));
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(request),null).andExpect(status().isUnauthorized());
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(request),"bad-token").andExpect(status().isUnauthorized());
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(request),token(TEACHER)).andExpect(status().isForbidden());
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(request),token(OTHER)).andExpect(status().isNotFound());
        call(put(path(UUID.randomUUID(),v(3))).contentType(APPLICATION_JSON).content(request),token).andExpect(status().isNotFound());
        jdbc.update("update attempts set attempt_source_type='TEST_VERSION' where attempt_id=?",attempt);
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(request),token).andExpect(status().isNotFound());
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=?",attempt));
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{\"selectedChoiceId\":1}","{\"selectedChoiceId\":\"bad\"}",
            "{\"selectedChoiceId\":\"1-1-1-1-1\"}","{\"selectedChoiceId\":null,\"clientSequence\":1}",
            "{\"selectedChoiceId\":null,\"isCorrect\":true}","{\"selectedChoiceId\":null,\"selectedChoiceId\":null}"})
    void strictPayloadRejectsAmbiguousOrSpoofedFields(String payload) throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(payload),token).andExpect(status().isBadRequest());
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=?",attempt));
    }
    @Test void queryParametersAndMalformedPathAreRejected() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));
        call(put(path(attempt,v(3))).param("actorId",OTHER.toString()).contentType(APPLICATION_JSON).content(answer(null)),token).andExpect(status().isBadRequest());
        call(put(API+"/"+attempt+"/answers/bad-id").contentType(APPLICATION_JSON).content(answer(null)),token).andExpect(status().isBadRequest());
    }
    @Test void serviceGuardsPermissionAndActorEvenWhenCalledWithoutController() throws Exception {
        var attempt=id(create(token(ACTOR),List.of(2),1));
        authenticate(ACTOR);
        assertThrows(AccessDeniedException.class,()->submissions.save(ACTOR,attempt,v(3),null));
        authenticate(OTHER,PERMISSION);
        assertThrows(AccessDeniedException.class,()->submissions.save(ACTOR,attempt,v(3),null));
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=?",attempt));
    }
    @Test void saveUsesPinnedVersionAfterRepublishAndArchive() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));var replacement=UUID.randomUUID();var newChoice=UUID.randomUUID();
        jdbc.update("""
                insert into question_versions(question_version_id,question_id,version_number,status,question_text,explanation,published_at,created_at)
                values (?,?,2,'PUBLISHED','Replacement','NEW_EXPLANATION',now(),now())
                """,replacement,q(3));
        jdbc.update("insert into question_choices(choice_id,question_version_id,choice_label,content,is_correct,display_order) values (?,?,'A','Replacement A',true,1)",newChoice,replacement);
        jdbc.update("update questions set current_published_version_id=?,status='ARCHIVED' where question_id=?",replacement,q(3));
        jdbc.update("update question_versions set status='ARCHIVED',archived_at=now() where question_version_id=?",v(3));
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(answer(choice(v(3),"A"))),token).andExpect(status().isOk());
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(answer(newChoice)),token)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CHOICE_VERSION_MISMATCH"));
        safe(read(token,attempt));
    }
    @Test void failureAfterSaveRollsBackToLastConfirmedSelection() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));var first=choice(v(3),"A");
        call(put(path(attempt,v(3))).contentType(APPLICATION_JSON).content(answer(first)),token).andExpect(status().isOk());
        authenticate(ACTOR,PERMISSION);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            submissions.save(ACTOR,attempt,v(3),choice(v(3),"B"));throw new IllegalStateException("Fixture rollback");
        }));
        assertEquals(first,jdbc.queryForObject("select selected_choice_id from attempt_answers where attempt_id=?",UUID.class,attempt));
    }
}
