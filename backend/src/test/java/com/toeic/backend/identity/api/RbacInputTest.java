package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class RbacInputTest {
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void normalizesRoleNameAndKeepsPartialNullSemantics() {
        var edit=json.readValue("{\"name\":\" qa_role \",\"description\":null}",RbacRequest.class).roleEdit(true);
        assertEquals("QA_ROLE",edit.name());assertTrue(edit.descriptionPresent());assertNull(edit.description());
        var partial=json.readValue("{\"description\":\"  Review team  \"}",RbacRequest.class).roleEdit(false);
        assertFalse(partial.namePresent());assertEquals("Review team",partial.description());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"name\":null}","{\"name\":true}","{\"name\":\"A\"}","{\"name\":\"ROLE_ADMIN\"}","{\"name\":\"CUSTOM\",\"permissionIds\":[1]}","{\"name\":\"CUSTOM\",\"name\":\"OTHER\"}"})
    void rejectsInvalidRoleEditsAndMassAssignment(String body) {
        assertThrows(RuntimeException.class,() -> json.readValue(body,RbacRequest.class).roleEdit(true));
    }
    @Test void acceptsTypedSortedDistinctIdsIncludingExplicitEmptySet() {
        assertEquals(List.of(1,3),json.readValue("{\"permissionIds\":[3,1]}",RbacRequest.class).ids("permissionIds"));
        assertEquals(List.of(),json.readValue("{\"roleIds\":[]}",RbacRequest.class).ids("roleIds"));
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"roleIds\":null}","{\"roleIds\":[0]}","{\"roleIds\":[1,1]}","{\"roleIds\":[\"1\"]}","{\"roleIds\":[1.0]}","{\"roleIds\":[2147483648]}","{\"roleIds\":[],\"userId\":\"spoof\"}"})
    void rejectsCoercionDuplicateAndUnknownAssignmentFields(String body) {
        assertThrows(RuntimeException.class,() -> json.readValue(body,RbacRequest.class).ids("roleIds"));
    }
    @Test void queryIsBoundedLiteralAndRejectsUnknownRepeatedParameters() {
        assertEquals("QA %_",RbacQuery.parse(Map.of("q",List.of(" QA  %_ "))).q());
        assertEquals(200L,RbacQuery.parse(Map.of("page",List.of("2"),"size",List.of("100"))).offset());
        assertThrows(RbacException.class,() -> RbacQuery.parse(Map.of("q",List.of("a","b"))));
        assertThrows(RbacException.class,() -> RbacQuery.parse(Map.of("permission",List.of("any"))));
        assertThrows(RbacException.class,() -> new RbacQuery("x",0,101));
    }
}
