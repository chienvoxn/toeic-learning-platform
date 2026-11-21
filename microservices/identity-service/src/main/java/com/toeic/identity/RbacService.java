package com.toeic.identity;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
@Service public class RbacService {
 private final JdbcTemplate jdbc;
 RbacService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Access(Set<String> roles,Set<String> permissions){}
 @Transactional(readOnly=true)
 public Access access(UUID userId){
  var roles=new HashSet<>(jdbc.queryForList("SELECT role_code FROM identity_user_roles WHERE user_id=?",String.class,userId));
  var permissions=new HashSet<>(jdbc.queryForList("SELECT DISTINCT rp.permission_code FROM identity_user_roles ur JOIN identity_role_permissions rp ON rp.role_code=ur.role_code WHERE ur.user_id=?",String.class,userId));
  if(roles.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"User has no roles");
  return new Access(Set.copyOf(roles),Set.copyOf(permissions));
 }
 @Transactional
 public void grant(UUID userId,String role){
  if(!Set.of("LEARNER","TEACHER","ADMIN").contains(role))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown role");
  jdbc.update("INSERT INTO identity_user_roles(user_id,role_code) VALUES (?,?) ON CONFLICT DO NOTHING",userId,role);
 }
}
