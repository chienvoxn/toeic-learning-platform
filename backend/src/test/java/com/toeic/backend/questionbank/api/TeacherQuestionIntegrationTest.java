package com.toeic.backend.questionbank.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeacherQuestionIntegrationTest extends QuestionAuthoringTestSupport {
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7})
    void crudAllParts(int part) throws Exception {
        var token=token(ACTOR);var group=Set.of(3,4,6,7).contains(part)?group(token):null;
        var created=create(token,part,group==null?null:gv(group,0));var id=qid(created);var version=qv(created,0);
        assertEquals("DRAFT",created.get("question").get("status").asString());
        assertTrue(created.get("question").get("publishedVersionId").isNull());
        assertEquals(ACTOR,jdbc.queryForObject("select created_by from questions where question_id=?",UUID.class,id));
        assertEquals(part==2?3:4,created.get("versions").get(0).get("choices").size());
        call(get(Q+"/"+id),token).andExpect(status().isOk());
        call(get(Q+"/"+id+"/history"),token).andExpect(status().isOk()).andExpect(jsonPath("$[0].questionVersionId").value(version.toString()));
        var edited=patchQ(token,id,"{\"questionText\":\"Updated\",\"explanation\":null,\"difficulty\":\"HARD\"}");
        assertEquals(version,qv(edited,0));assertTrue(edited.get("versions").get(0).get("explanation").isNull());
        call(get(Q).param("partId",created.get("question").get("partId").asString()),token).andExpect(jsonPath("$.totalElements").value(1));
        call(get("/api/v1/questions/"+id),token).andExpect(status().isNotFound());
        call(delete(Q+"/"+id),token).andExpect(status().isNoContent());
        call(delete(Q+"/"+id),token).andExpect(status().isNoContent());
        call(patch(Q+"/"+id).contentType(APPLICATION_JSON).content("{\"questionText\":\"blocked\"}"),token).andExpect(status().isConflict());
        assertEquals(1,count("select count(*) from question_versions where question_id=?",id));safe(edited.toString());
    }
    @Test void groupCrud() throws Exception {
        var token=token(ACTOR);var created=group(token);var id=gid(created);
        assertEquals(ACTOR,jdbc.queryForObject("select created_by from question_groups where group_id=?",UUID.class,id));
        patchG(token,id,"{\"instruction\":null,\"passageText\":\"Changed\",\"groupType\":\"TALK\"}");
        call(get(G+"/"+id+"/history"),token).andExpect(status().isOk()).andExpect(jsonPath("$[0].instruction").isEmpty());
        call(get(G).param("groupType","TALK"),token).andExpect(jsonPath("$.totalElements").value(1));
        call(delete(G+"/"+id),token).andExpect(status().isNoContent());call(delete(G+"/"+id),token).andExpect(status().isNoContent());
        call(patch(G+"/"+id).contentType(APPLICATION_JSON).content("{\"passageText\":\"blocked\"}"),token).andExpect(status().isConflict());
        call(get(G+"/"+id),token).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={Q,G})
    void missingOrBadAuthenticationAndLearner(String path) throws Exception {
        for(var method:List.of("GET","POST","PATCH","DELETE")) {
            var url=Set.of("PATCH","DELETE").contains(method)?path+"/"+UUID.randomUUID():path;
            call(request(org.springframework.http.HttpMethod.valueOf(method),url).contentType(APPLICATION_JSON).content("{}"),null).andExpect(status().isUnauthorized());
        }
        call(get(path),"invalid-session").andExpect(status().isUnauthorized());call(get(path),token(LEARNER)).andExpect(status().isForbidden());
    }
    @Test void ownAndAnyScopesAndCount() throws Exception {
        var mine=token(ACTOR);var other=token(OTHER);var admin=token(ADMIN);
        var first=create(mine,1,null);var foreign=create(other,1,null);var ownGroup=group(mine);var foreignGroup=group(other);
        call(get(Q+"/"+qid(foreign)),mine).andExpect(status().isNotFound());
        call(get(Q+"/"+qid(foreign)+"/history"),mine).andExpect(status().isNotFound());
        call(patch(Q+"/"+qid(foreign)).contentType(APPLICATION_JSON).content("{\"questionText\":\"steal\"}"),mine).andExpect(status().isNotFound());
        call(delete(Q+"/"+qid(foreign)),mine).andExpect(status().isNotFound());
        call(get(G+"/"+gid(foreignGroup)),mine).andExpect(status().isNotFound());call(delete(G+"/"+gid(foreignGroup)),mine).andExpect(status().isNotFound());
        call(get(Q).param("size","1"),mine).andExpect(jsonPath("$.totalElements").value(1));
        call(get(Q).param("page","5"),mine).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content").isEmpty());
        call(get(G),mine).andExpect(jsonPath("$.totalElements").value(1));call(get(Q),admin).andExpect(jsonPath("$.totalElements").value(2));
        call(get(G),admin).andExpect(jsonPath("$.totalElements").value(2));patchQ(admin,qid(first),"{\"questionText\":\"Admin\"}");patchG(admin,gid(ownGroup),"{\"passageText\":\"Admin\"}");
        call(get(Q+"/"+UUID.randomUUID()),mine).andExpect(status().isNotFound());
    }
    @Test void permissionsAreNotRoleHardCodedAndRevocationAppliesNextRequest() throws Exception {
        jdbc.update("insert into roles(name,description) values ('Q03_CUSTOM','test only')");
        jdbc.update("insert into role_permissions(role_id,permission_id) select r.role_id,p.permission_id from roles r cross join permissions p where r.name='Q03_CUSTOM' and p.code='question:manage:self'");
        jdbc.update("insert into user_roles(user_id,role_id) select ?,role_id from roles where name='Q03_CUSTOM'",LEARNER);
        var token=token(LEARNER);create(token,1,null);jdbc.update("delete from user_roles where user_id=?",LEARNER);
        call(get(Q),token).andExpect(status().isForbidden());
    }
    @Test void serviceRejectsActorSpoofAndMissingPermission() throws Exception {
        var edit=command(payload(1,null));authenticate(OTHER,"question:manage:self");
        assertThrows(AccessDeniedException.class,()->service.createQuestion(ACTOR,edit));authenticate(ACTOR,"question:read");
        assertThrows(AccessDeniedException.class,()->service.createQuestion(ACTOR,edit));
    }
    @ParameterizedTest @ValueSource(strings={"owner","ownerId","createdBy","creator","status","publishedVersionId","currentPublishedVersionId","versionNumber","questionId","role","permission","storageUrl","url","transcript"})
    void payloadAllowlist(String field) throws Exception {
        var body=payload(1,null);body.put(field,"bad");call(post(Q).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)),token(ACTOR)).andExpect(status().isBadRequest());
        assertEquals(0,count("select count(*) from questions where created_by=?",ACTOR));
    }
    @ParameterizedTest @ValueSource(strings={"page=-1","size=101","size=0","sort=questionText,asc","sort=updatedAt,drop","unknown=x","partId=0","topicId=x","status=IN_REVIEW"})
    void queryValidation(String input) throws Exception { var p=input.split("=",2);call(get(Q).param(p[0],p[1]),token(ACTOR)).andExpect(status().isBadRequest()); }
    @Test void parametersAndBodyContracts() throws Exception {
        var token=token(ACTOR);call(get(Q).param("page","0","1"),token).andExpect(status().isBadRequest());
        call(get(G).param("partId","1"),token).andExpect(status().isBadRequest());
        call(post(Q).param("owner",OTHER.toString()).contentType(APPLICATION_JSON).content(json.writeValueAsString(payload(1,null))),token).andExpect(status().isBadRequest());
        call(delete(G+"/"+UUID.randomUUID()).contentType(APPLICATION_JSON).content("{}"),token).andExpect(status().isBadRequest());call(get(Q+"/not-a-uuid"),token).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"noCorrect","twoCorrect","duplicateLabel","duplicateOrder","wrongLabel","wrongOrder","tooFew","tooMany","blankContent","nullChoices","badDifficulty","unknownPart","unknownType","unknownTopic","blankText","stringId"})
    void invalidCreateAndPatchRollBack(String problem) throws Exception {
        var token=token(ACTOR);var original=create(token,1,null);var body=payload(1,null);var list=new ArrayList<>(choices(1));
        switch(problem) {
            case "noCorrect" -> list.set(0,Map.of("label","A","content","A","isCorrect",false,"displayOrder",1));
            case "twoCorrect" -> list.set(1,Map.of("label","B","content","B","isCorrect",true,"displayOrder",2));
            case "duplicateLabel" -> list.set(1,Map.of("label","A","content","B","isCorrect",false,"displayOrder",2));
            case "duplicateOrder" -> list.set(1,Map.of("label","B","content","B","isCorrect",false,"displayOrder",1));
            case "wrongLabel" -> list.set(1,Map.of("label","Z","content","B","isCorrect",false,"displayOrder",2));
            case "wrongOrder" -> list.set(1,Map.of("label","B","content","B","isCorrect",false,"displayOrder",8));
            case "tooFew" -> list.removeLast();case "tooMany" -> list.add(Map.of("label","E","content","E","isCorrect",false,"displayOrder",5));
            case "blankContent" -> list.set(1,Map.of("label","B","content"," ","isCorrect",false,"displayOrder",2));
            case "nullChoices" -> body.put("choices",null);case "badDifficulty" -> body.put("difficulty","IMPOSSIBLE");
            case "unknownPart" -> body.put("partId",Integer.MAX_VALUE);case "unknownType" -> body.put("questionTypeId",Integer.MAX_VALUE);
            case "unknownTopic" -> body.put("topicId",Integer.MAX_VALUE);case "blankText" -> body.put("questionText"," ");case "stringId" -> body.put("partId","1");
            default -> throw new IllegalStateException();
        }
        if(!problem.equals("nullChoices"))body.put("choices",list);
        call(post(Q).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)),token).andExpect(status().isBadRequest());
        call(patch(Q+"/"+qid(original)).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)),token).andExpect(status().isBadRequest());
        assertEquals("Q03 original prompt",jdbc.queryForObject("select question_text from question_versions where question_version_id=?",String.class,qv(original,0)));
        assertEquals(1,count("select count(*) from questions where created_by=?",ACTOR));
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7})
    void groupRequirementsAndForeignPin(int part) throws Exception {
        var token=token(ACTOR);var group=group(token);var required=Set.of(3,4,6,7).contains(part);
        call(post(Q).contentType(APPLICATION_JSON).content(json.writeValueAsString(payload(part,required?null:gv(group,0)))),token).andExpect(status().isBadRequest());
        var foreign=group(token(OTHER));call(post(Q).contentType(APPLICATION_JSON).content(json.writeValueAsString(payload(part,gv(foreign,0)))),token).andExpect(status().isNotFound());
    }
    @Test void legacyGroupOwnerIsNotInferred() throws Exception {
        var id=UUID.fromString("c3000000-0000-0000-0000-000000000999");
        jdbc.update("insert into question_groups(group_id,group_type,status,created_at,updated_at) values (?,'PASSAGE','DRAFT',now(),now())",id);
        jdbc.update("insert into question_group_versions(group_version_id,group_id,version_number,status,created_at) values (?, ?,1,'DRAFT',now())",UUID.randomUUID(),id);
        call(get(G+"/"+id),token(ACTOR)).andExpect(status().isNotFound());call(get(G+"/"+id),token(ADMIN)).andExpect(status().isOk());
        assertNull(jdbc.queryForObject("select created_by from question_groups where group_id=?",UUID.class,id));
    }
    @Test void equalTimestampSortAndPaginationUseStableQuestionId() throws Exception {
        var token=token(ACTOR);var first=create(token,1,null);var second=create(token,1,null);
        jdbc.update("update questions set updated_at='2026-01-01T00:00:00Z' where created_by=?",ACTOR);
        var ids=List.of(qid(first),qid(second)).stream().sorted(Comparator.comparing((UUID id) -> id.toString())).toList();
        call(get(Q).param("size","1"),token).andExpect(jsonPath("$.content[0].questionId").value(ids.get(0).toString()));
        call(get(Q).param("size","1").param("page","1"),token).andExpect(jsonPath("$.content[0].questionId").value(ids.get(1).toString()));
        call(get(Q).param("sort","questionId,desc"),token).andExpect(jsonPath("$.content[0].questionId").value(ids.get(1).toString()));
    }
    @Test void archivedQuestionRetainsChoicesAssetsAndHistoryButDisappearsFromReadCount() throws Exception {
        var token=token(ACTOR);var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(1),"transcript","History")));
        var created=create(token,body);var id=qid(created);var version=qv(created,0);publish(false,id,version);
        call(delete(Q+"/"+id),token).andExpect(status().isNoContent());
        call(get("/api/v1/questions/"+id),token).andExpect(status().isNotFound());call(get("/api/v1/questions"),token).andExpect(jsonPath("$.totalElements").value(0));
        assertEquals(version,jdbc.queryForObject("select current_published_version_id from questions where question_id=?",UUID.class,id));
        assertEquals(4,count("select count(*) from question_choices where question_version_id=?",version));
        assertEquals(1,count("select count(*) from question_resources where question_version_id=?",version));
        call(get(Q+"/"+id+"/history"),token).andExpect(status().isOk()).andExpect(jsonPath("$[0].assets[0].transcript").value("History"));
    }
    @Test void wrongGroupVersionAndPublishedMetadataChangeAreAtomic() throws Exception {
        var token=token(ACTOR);var first=group(token);var second=group(token);
        var question=create(token,3,gv(first,0));publish(false,qid(question),qv(question,0));
        call(patch(Q+"/"+qid(question)).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("groupVersionId",gv(second,0),"questionText","rollback"))),token).andExpect(status().isConflict());
        assertEquals(1,count("select count(*) from question_versions where question_id=?",qid(question)));
        assertEquals(gv(first,0),jdbc.queryForObject("select group_version_id from question_versions where question_version_id=?",UUID.class,qv(question,0)));
        var missing=payload(3,UUID.randomUUID());call(post(Q).contentType(APPLICATION_JSON).content(json.writeValueAsString(missing)),token).andExpect(status().isNotFound());
    }
}
