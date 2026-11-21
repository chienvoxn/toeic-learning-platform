package com.toeic.membership;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MembershipTest {
 @Test void planMutationPreservesOwnerAndStatus() {
  UUID id=UUID.randomUUID(); Membership membership=new Membership(id,"FREE");
  membership.changePlan("PRO");
  assertEquals(id,membership.getUserId());
  assertEquals("PRO",membership.getPlanCode());
  assertEquals("ACTIVE",membership.getStatus());
 }
}
