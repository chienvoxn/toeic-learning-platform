package com.toeic.identity;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class TokenServiceTest {
 private final TokenService token = new TokenService("test-only-secret-should-be-thirtytwo-characters-long",new ObjectMapper());
 @Test void validSignedJwtContainsSubjectAndPermissions() {
  UUID id=UUID.randomUUID();String raw=token.issue(id,Set.of("LEARNER"),Set.of("assessment:submit"));
  var actor=token.require("Bearer "+raw);assertEquals(id,actor.userId());assertTrue(actor.can("assessment:submit"));assertFalse(actor.can("content:write"));
 }
 @Test void tamperedSignatureRejected() {
  String raw=token.issue(UUID.randomUUID(),Set.of("LEARNER"),Set.of("assessment:submit"));
  char last=raw.charAt(raw.length()-1);String altered=raw.substring(0,raw.length()-1)+(last=='A'?'B':'A');
  assertThrows(ResponseStatusException.class,()->token.require("Bearer "+altered));
 }
 @Test void missingBearerRejected(){assertThrows(ResponseStatusException.class,()->token.require(null));}
}
