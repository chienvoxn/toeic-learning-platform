package com.toeic.backend.system.application;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AdminReadQueryTest {
    @Test void utcHalfOpenWindowAndStableDefaultSort() {
        var q=AdminReadQuery.audit(Map.of("from",List.of("2026-11-13T07:00:00+07:00"),"to",List.of("2026-11-14T00:00:00Z")));
        assertEquals("2026-11-13T00:00Z",q.window().from().toString());assertEquals("createdAt,desc",q.sort());assertEquals(20,q.size());
        var now=Instant.parse("2026-11-13T12:00:00Z");var w=AdminReadQuery.usage(Map.of(),Clock.fixed(now,ZoneOffset.UTC));
        assertEquals(now,w.to().toInstant());assertEquals(now.minusSeconds(30*86400),w.from().toInstant());
    }
    @ParameterizedTest @ValueSource(strings={"page=-1","size=0","size=101","sort=details,desc","sort=createdAt,desc;drop","actor=1-1-1-1-1","entityId=1","from=2026-11-13","from=invalid","unknown=x","entityType=role","action=TOKEN x","page=2147483648"})
    void rejectsUnsafeMalformedFilters(String input) {
        var pair=input.split("=",2);assertThrows(AdminReadException.class,() -> AdminReadQuery.audit(Map.of(pair[0],List.of(pair[1]))));
    }
    @Test void validatesWindowDuplicatesAndIntegerRoleId() {
        assertThrows(AdminReadException.class,() -> AdminReadQuery.audit(Map.of("page",List.of("0","1"))));
        assertThrows(AdminReadException.class,() -> AdminReadQuery.audit(Map.of("from",List.of("2026-11-14T00:00Z"),"to",List.of("2026-11-13T00:00Z"))));
        assertThrows(AdminReadException.class,() -> AdminReadQuery.audit(Map.of("entityType",List.of("ROLE"),"entityId",List.of("2147483648"))));
        assertEquals("42",AdminReadQuery.audit(Map.of("entityType",List.of("ROLE"),"entityId",List.of("42"))).entityId());
    }
}
