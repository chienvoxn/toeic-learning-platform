package com.toeic.assessment;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
@Component public class TokenService {
 private final String secret; private final ObjectMapper json;
 public TokenService(@Value("${auth.jwt-secret}") String secret,ObjectMapper json) {
  if(secret.length()<32)throw new IllegalArgumentException("JWT secret must be >=32 chars");this.secret=secret;this.json=json;
 }
 public record Actor(UUID userId,Set<String> roles,Set<String> permissions) {
  public boolean hasRole(String role){return roles.contains(role);}
  public boolean can(String permission){return permissions.contains(permission)||roles.contains("ADMIN");}
 }
 private byte[] mac(String text) throws Exception {
  Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  return m.doFinal(text.getBytes(StandardCharsets.US_ASCII));
 }
 public String issue(UUID id,String role) {
  try {
   String h=Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(Map.of("alg","HS256","typ","JWT")));
   long now=Instant.now().getEpochSecond();
   List<String> permissions=switch(role){case "ADMIN"->List.of("content:write","assessment:submit","interaction:write","membership:read");case "TEACHER"->List.of("content:write","assessment:submit","interaction:write","membership:read");default->List.of("assessment:submit","interaction:write","membership:read");};
   Map<String,Object> claims=new LinkedHashMap<>();claims.put("iss","toeic-identity");claims.put("aud","toeic-services");claims.put("sub",id.toString());claims.put("roles",List.of(role));claims.put("permissions",permissions);claims.put("iat",now);claims.put("exp",now+1800);
   String b=Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(claims));String data=h+"."+b;
   return data+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac(data));
  }catch(Exception e){throw new IllegalStateException("Cannot sign token",e);}
 }
 public Actor require(String auth) {
  if(auth==null||!auth.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Missing bearer");
  try {
   String[] parts=auth.substring(7).split("\\.",-1);if(parts.length!=3)throw new IllegalArgumentException();
   JsonNode header=json.readTree(Base64.getUrlDecoder().decode(parts[0]));
   if(!"HS256".equals(header.path("alg").asText())||!"JWT".equals(header.path("typ").asText()))throw new IllegalArgumentException();
   if(!MessageDigest.isEqual(mac(parts[0]+"."+parts[1]),Base64.getUrlDecoder().decode(parts[2])))throw new IllegalArgumentException();
   JsonNode claims=json.readTree(Base64.getUrlDecoder().decode(parts[1]));long now=Instant.now().getEpochSecond();
   if(!"toeic-identity".equals(claims.path("iss").asText())||!"toeic-services".equals(claims.path("aud").asText())||claims.path("exp").asLong()<=now||claims.path("iat").asLong()>now+30)throw new IllegalArgumentException();
   Set<String> roles=new HashSet<>(),perms=new HashSet<>();for(JsonNode v:claims.path("roles"))roles.add(v.asText());for(JsonNode v:claims.path("permissions"))perms.add(v.asText());
   return new Actor(UUID.fromString(claims.path("sub").asText()),Set.copyOf(roles),Set.copyOf(perms));
  }catch(Exception ex){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid token");}
 }
}
