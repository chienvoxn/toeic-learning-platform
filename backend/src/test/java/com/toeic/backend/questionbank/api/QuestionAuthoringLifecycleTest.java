package com.toeic.backend.questionbank.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuestionAuthoringLifecycleTest extends QuestionAuthoringTestSupport {
    @ParameterizedTest @ValueSource(ints={1,5,6})
    void ownedAndGrantedAudioImageAndVersionTranscript(int resource) throws Exception {
        var token=token(ACTOR);var body=payload(1,null);
        body.put("assets",List.of(resource==6?Map.of("mediaId",media(resource)):Map.of("mediaId",media(resource),"transcript","VERSION_TRANSCRIPT")));
        var created=create(token,body);var asset=created.get("versions").get(0).get("assets").get(0);
        assertEquals(media(resource).toString(),asset.get("mediaId").asString());
        if(resource!=6)assertEquals("VERSION_TRANSCRIPT",asset.get("transcript").asString());
        safe(created.toString());assertEquals("Q03_LEGACY_TRANSCRIPT",jdbc.queryForObject("select transcript from media_resources where resource_id=?",String.class,media(1)));
    }
    @ParameterizedTest @ValueSource(ints={2,3,4,7,8,99})
    void forbiddenPrivateLegacyRevokedMissingAndWrongTypeMediaAreGeneric404(int resource) throws Exception {
        var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(resource))));
        var response=call(post(Q).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)),token(ACTOR))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        assertFalse(response.andReturn().getResponse().getContentAsString().contains(media(resource).toString()));
        assertEquals(0,count("select count(*) from questions where created_by=?",ACTOR));
    }
    @Test void anyPermissionDoesNotBypassMediaReuse() throws Exception {
        var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(1))));
        call(post(Q).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)),token(ADMIN)).andExpect(status().isNotFound());
    }
    @Test void omittedNullEmptyReplaceAssetsAndInvalidTranscriptRollback() throws Exception {
        var token=token(ACTOR);var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(1),"transcript","First")));
        var created=create(token,body);var id=qid(created);var kept=patchQ(token,id,"{\"questionText\":\"Keep media\"}");
        assertEquals("First",kept.get("versions").get(0).get("assets").get(0).get("transcript").asString());
        call(patch(Q+"/"+id).contentType(APPLICATION_JSON).content("{\"questionText\":\"rollback\",\"assets\":null}"),token).andExpect(status().isBadRequest());
        call(patch(Q+"/"+id).contentType(APPLICATION_JSON).content("{\"assets\":[{\"mediaId\":\""+media(6)+"\",\"transcript\":\"bad\"}]}"),token).andExpect(status().isBadRequest());
        assertEquals("Keep media",jdbc.queryForObject("select question_text from question_versions where question_version_id=?",String.class,qv(created,0)));
        var replaced=patchQ(token,id,"{\"assets\":[{\"mediaId\":\""+media(6)+"\"}]}");
        assertEquals(media(6).toString(),replaced.get("versions").get(0).get("assets").get(0).get("mediaId").asString());
        assertTrue(patchQ(token,id,"{\"assets\":[]}").get("versions").get(0).get("assets").isEmpty());
    }
    @Test void revokedMediaCanBeRemovedButCannotBeRetainedByTextOnlyUpdate() throws Exception {
        var token=token(ACTOR);var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(5),"transcript","Pinned")));
        var created=create(token,body);revokeGrant();
        call(patch(Q+"/"+qid(created)).contentType(APPLICATION_JSON).content("{\"questionText\":\"blocked\"}"),token).andExpect(status().isConflict());
        patchQ(token,qid(created),"{\"assets\":[],\"questionText\":\"Removed\"}");
        assertEquals(0,count("select count(*) from question_resources where question_version_id=?",qv(created,0)));
    }
    @Test void publishedQuestionWorkingDraftNeverChangesPublishedTextChoicesAssetsOrPointer() throws Exception {
        var token=token(ACTOR);var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(1),"transcript","Published transcript")));
        var created=create(token,body);var id=qid(created);var published=qv(created,0);publish(false,id,published);
        var before=jdbc.queryForMap("select * from question_versions where question_version_id=?",published);
        var choicesBefore=jdbc.queryForList("select * from question_choices where question_version_id=? order by display_order",published);
        var assetsBefore=jdbc.queryForList("select * from question_resources where question_version_id=?",published);
        var edited=patchQ(token,id,"{\"questionText\":\"Working text\",\"explanation\":\"Working explanation\",\"assets\":[]}");
        assertEquals(2,edited.get("versions").size());var draft=qv(edited,1);assertNotEquals(published,draft);
        patchQ(token,id,"{\"difficulty\":\"HARD\"}");
        var newChoices=new ArrayList<>(choices(1));newChoices.set(0,Map.of("label","A","content","New A","isCorrect",false,"displayOrder",1));
        newChoices.set(1,Map.of("label","B","content","New B","isCorrect",true,"displayOrder",2));patchQ(token,id,json.writeValueAsString(Map.of("choices",newChoices)));
        assertEquals(2,count("select count(*) from question_versions where question_id=?",id));
        assertEquals(before,jdbc.queryForMap("select * from question_versions where question_version_id=?",published));
        assertEquals(choicesBefore,jdbc.queryForList("select * from question_choices where question_version_id=? order by display_order",published));
        assertEquals(assetsBefore,jdbc.queryForList("select * from question_resources where question_version_id=?",published));
        assertEquals(published,jdbc.queryForObject("select current_published_version_id from questions where question_id=?",UUID.class,id));
        var read=tree(call(get("/api/v1/questions/"+id),token).andExpect(status().isOk()));
        assertEquals("Q03 original prompt",read.get("questionText").asString());readSafe(read);
        call(patch(Q+"/"+id).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("partId",body.get("partId"),"questionText","Must rollback"))),token).andExpect(status().isConflict());
        assertEquals("Working text",jdbc.queryForObject("select question_text from question_versions where question_version_id=?",String.class,draft));
    }
    @Test void cloneChecksAllSourceMediaEvenIfPayloadDropsThem() throws Exception {
        var token=token(ACTOR);var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(5))));
        var created=create(token,body);publish(false,qid(created),qv(created,0));revokeGrant();
        call(patch(Q+"/"+qid(created)).contentType(APPLICATION_JSON).content("{\"questionText\":\"bad clone\",\"assets\":[]}"),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUESTION_MEDIA_REUSE_CONFLICT"));
        assertEquals(1,count("select count(*) from question_versions where question_id=?",qid(created)));
        call(get("/api/v1/questions/"+qid(created)),token).andExpect(status().isOk());
    }
    @Test void groupWorkingDraftExplicitPinAndArchiveHideQ02WithoutCascading() throws Exception {
        var token=token(ACTOR);var group=group(token);var id=gid(group);var original=gv(group,0);publish(true,id,original);
        var question=create(token,3,original);var questionId=qid(question);var version=qv(question,0);publish(false,questionId,version);
        var before=jdbc.queryForMap("select * from question_group_versions where group_version_id=?",original);
        var edited=patchG(token,id,"{\"passageText\":\"Working passage\",\"assets\":[{\"mediaId\":\""+media(1)+"\",\"transcript\":\"Working transcript\"}]}");
        assertEquals(2,edited.get("versions").size());patchG(token,id,"{\"instruction\":\"Working instruction\"}");
        assertEquals(before,jdbc.queryForMap("select * from question_group_versions where group_version_id=?",original));
        assertEquals(2,count("select count(*) from question_group_versions where group_id=?",id));
        assertEquals(original,jdbc.queryForObject("select group_version_id from question_versions where question_version_id=?",UUID.class,version));
        var read=tree(call(get("/api/v1/questions/"+questionId),token).andExpect(status().isOk()));
        assertEquals("Published passage",read.get("passage").get("passageText").asString());readSafe(read);
        var next=gv(edited,1);var repinned=patchQ(token,questionId,json.writeValueAsString(Map.of("groupVersionId",next,"questionText","Explicit pin")));
        assertEquals(next.toString(),repinned.get("versions").get(1).get("groupVersionId").asString());
        assertEquals(original,jdbc.queryForObject("select group_version_id from question_versions where question_version_id=?",UUID.class,version));
        call(patch(G+"/"+id).contentType(APPLICATION_JSON).content("{\"groupType\":\"OTHER\",\"passageText\":\"bad\"}"),token).andExpect(status().isConflict());
        call(get("/api/v1/questions"),token).andExpect(jsonPath("$.totalElements").value(1));
        call(delete(G+"/"+id),token).andExpect(status().isNoContent());call(delete(G+"/"+id),token).andExpect(status().isNoContent());
        call(get("/api/v1/questions/"+questionId),token).andExpect(status().isNotFound());call(get("/api/v1/questions"),token).andExpect(jsonPath("$.totalElements").value(0));
        assertEquals("PUBLISHED",jdbc.queryForObject("select status from questions where question_id=?",String.class,questionId));
        assertEquals(version,jdbc.queryForObject("select current_published_version_id from questions where question_id=?",UUID.class,questionId));
        call(get(Q+"/"+questionId),token).andExpect(status().isOk());
        call(patch(Q+"/"+questionId).contentType(APPLICATION_JSON).content("{\"questionText\":\"blocked\"}"),token).andExpect(status().isConflict());
    }
    @Test void privateMediaAndTranscriptAreHiddenInManagementAndRead() throws Exception {
        var token=token(ACTOR);var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(1),"transcript","SENSITIVE_VERSION_TRANSCRIPT")));
        var created=create(token,body);publish(false,qid(created),qv(created,0));jdbc.update("update media_resources set visibility='PRIVATE' where resource_id=?",media(1));
        var managed=tree(call(get(Q+"/"+qid(created)),token).andExpect(status().isOk()));
        assertTrue(managed.get("versions").get(0).get("assets").isEmpty());assertFalse(managed.toString().contains("SENSITIVE_VERSION_TRANSCRIPT")||managed.toString().contains(media(1).toString()));
        var read=tree(call(get("/api/v1/questions/"+qid(created)),token).andExpect(status().isOk()));readSafe(read);assertFalse(read.toString().contains(media(1).toString()));
    }
    @Test void invalidPointerAndMultipleDraftsAreNotGuessed() throws Exception {
        var token=token(ACTOR);var created=create(token,1,null);var id=qid(created);jdbc.update("update questions set status='PUBLISHED' where question_id=?",id);
        call(patch(Q+"/"+id).contentType(APPLICATION_JSON).content("{\"questionText\":\"no pointer\"}"),token).andExpect(status().isConflict());
        jdbc.update("update questions set status='DRAFT' where question_id=?",id);
        jdbc.update("insert into question_versions(question_version_id,question_id,version_number,question_text,status,created_at) values (?, ?,2,'Ambiguous','DRAFT',now())",UUID.randomUUID(),id);
        call(patch(Q+"/"+id).contentType(APPLICATION_JSON).content("{\"questionText\":\"ambiguous\"}"),token).andExpect(status().isConflict());
        assertEquals("Q03 original prompt",jdbc.queryForObject("select question_text from question_versions where question_version_id=?",String.class,qv(created,0)));
    }
    @Test void groupedAssetsAreRevalidatedAndPrivatePassageTranscriptsAreHidden() throws Exception {
        var token=token(ACTOR);var created=group(token);var id=gid(created);
        patchG(token,id,"{\"assets\":[{\"mediaId\":\""+media(5)+"\",\"transcript\":\"PASSAGE_TRANSCRIPT\"}]}");
        revokeGrant();call(post(Q).contentType(APPLICATION_JSON).content(json.writeValueAsString(payload(3,gv(created,0)))),token).andExpect(status().isConflict());
        call(patch(G+"/"+id).contentType(APPLICATION_JSON).content("{\"passageText\":\"blocked\"}"),token).andExpect(status().isConflict());
        patchG(token,id,"{\"assets\":[]}");
        assertEquals(0,count("select count(*) from question_group_version_resources where group_version_id=?",gv(created,0)));
    }
    @Test void authenticatedMediaRequiresReadPermissionEvenWithReuseGrant() throws Exception {
        var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(5))));var edit=command(body);
        authenticate(ACTOR,"question:manage:self");
        var error=assertThrows(com.toeic.backend.questionbank.application.QuestionAuthoringException.class,()->service.createQuestion(ACTOR,edit));
        assertEquals(404,error.getStatus());assertEquals(0,count("select count(*) from questions where created_by=?",ACTOR));
    }
    @Test void explicitNewPinRepairsHiddenOldPinWithoutChangingHistoricalVersions() throws Exception {
        var token=token(ACTOR);var group=group(token);var groupId=gid(group);var old=gv(group,0);publish(true,groupId,old);
        var question=create(token,3,old);publish(false,qid(question),qv(question,0));
        var edited=patchG(token,groupId,"{\"passageText\":\"Next published passage\"}");var next=gv(edited,1);publish(true,groupId,next);
        jdbc.update("update question_group_versions set status='ARCHIVED',archived_at=now() where group_version_id=?",old);
        call(get("/api/v1/questions/"+qid(question)),token).andExpect(status().isNotFound());
        call(get("/api/v1/questions"),token).andExpect(jsonPath("$.totalElements").value(0));
        var repinned=patchQ(token,qid(question),json.writeValueAsString(Map.of("groupVersionId",next)));
        assertEquals(next.toString(),repinned.get("versions").get(1).get("groupVersionId").asString());
        assertEquals(old,jdbc.queryForObject("select group_version_id from question_versions where question_version_id=?",UUID.class,qv(question,0)));
        assertEquals("ARCHIVED",jdbc.queryForObject("select status from question_group_versions where group_version_id=?",String.class,old));
        call(get("/api/v1/questions/"+qid(question)),token).andExpect(status().isNotFound());
    }
}
