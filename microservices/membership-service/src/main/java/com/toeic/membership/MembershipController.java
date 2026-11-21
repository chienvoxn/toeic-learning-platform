package com.toeic.membership;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/v1/memberships") public class MembershipController {
 private final IdentityClient identity; private final MembershipRepository repository;
 public MembershipController(IdentityClient identity,MembershipRepository repository){this.identity=identity;this.repository=repository;}
 public record View(UUID userId,String planCode,String status,int maxPracticePerDay){}
 public record Change(@NotNull @Pattern(regexp="FREE|PRO") String planCode){}
 @org.springframework.beans.factory.annotation.Value("${services.internal-key}") private String internalKey;
 @GetMapping("/internal/{userId}") @Transactional(readOnly=true)
 public View internal(@PathVariable UUID userId,@RequestHeader(value="X-Service-Key",required=false) String key) {
  if(key==null||!java.security.MessageDigest.isEqual(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),internalKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  var m=repository.findByUserId(userId);String code=m.map(Membership::getPlanCode).orElse("FREE");
  return new View(userId,code,"ACTIVE","PRO".equals(code)?1000:10);
 }
 @GetMapping("/me") @Transactional(readOnly=true)
 public View me(@RequestHeader(value="Authorization",required=false) String bearer){
  UUID user=identity.requireUser(bearer);var m=repository.findByUserId(user);String code=m.map(Membership::getPlanCode).orElse("FREE");
  return new View(user,code,m.map(Membership::getStatus).orElse("ACTIVE"),"PRO".equals(code)?1000:10);
 }
 // Demo-only self enrollment: PRO plan is NOT purchase/payment or trusted entitlement issuance.
 // Production must restrict this endpoint to an admin or verified billing event.
 @PutMapping("/me") @Transactional
 public View setForDemo(@RequestHeader(value="Authorization",required=false) String bearer,@Valid @RequestBody Change body){
  UUID user=identity.requireUser(bearer);
  if(!"true".equalsIgnoreCase(System.getenv().getOrDefault("ALLOW_DEMO_PLAN_CHANGE","false")))
     throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Demo plan changes disabled");
  var m=repository.findByUserId(user).orElseGet(()->new Membership(user,"FREE"));m.changePlan(body.planCode());repository.save(m);
  return new View(user,m.getPlanCode(),m.getStatus(),"PRO".equals(m.getPlanCode())?1000:10);
 }
}
