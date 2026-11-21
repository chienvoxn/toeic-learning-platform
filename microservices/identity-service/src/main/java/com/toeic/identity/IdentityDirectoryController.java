package com.toeic.identity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@Entity @Table(name="identity_users") class IdentityUser {
 @Id UUID userId; @Column(nullable=false,unique=true) String email; @Column(nullable=false) String displayName;
 @Column(nullable=false) String passwordHash;@Column(nullable=false) String role;
 protected IdentityUser(){} IdentityUser(String email,String name,String hash){userId=UUID.randomUUID();this.email=email;displayName=name;passwordHash=hash;role="LEARNER";}
}
interface IdentityUserRepository extends JpaRepository<IdentityUser,UUID>{Optional<IdentityUser> findByEmailIgnoreCase(String email);}
@RestController @RequestMapping("/ms/v1/identity") class IdentityDirectoryController {
 private final IdentityUserRepository repo;private final TokenService tokens;private final RbacService rbac;private final BCryptPasswordEncoder bcrypt=new BCryptPasswordEncoder();
 IdentityDirectoryController(IdentityUserRepository repo,TokenService tokens,RbacService rbac){this.repo=repo;this.tokens=tokens;this.rbac=rbac;}
 record Register(@NotBlank @Email String email,@NotBlank @Size(min=2,max=120) String displayName,@NotBlank @Size(min=8,max=72) String password){}
 record Login(@NotBlank String email,@NotBlank String password){}
 record UserView(UUID userId,String email,String displayName,String role){}
 record Session(String accessToken,String tokenType,long expiresIn,UserView user){}
 Session session(IdentityUser u){var access=rbac.access(u.userId);return new Session(tokens.issue(u.userId,access.roles(),access.permissions()),"Bearer",1800,view(u));}
 UserView view(IdentityUser u){return new UserView(u.userId,u.email,u.displayName,u.role);}
 @PostMapping("/register") @Transactional
 Session register(@Valid @RequestBody Register r){String email=r.email().trim().toLowerCase(Locale.ROOT);if(repo.findByEmailIgnoreCase(email).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
  var u=repo.saveAndFlush(new IdentityUser(email,r.displayName().trim(),bcrypt.encode(r.password())));rbac.grant(u.userId,"LEARNER");return session(u);}
 @PostMapping("/login") @Transactional(readOnly=true)
 Session login(@Valid @RequestBody Login r){var u=repo.findByEmailIgnoreCase(r.email().trim()).orElse(null);
  if(u==null||!bcrypt.matches(r.password(),u.passwordHash))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
  return session(u);}
 @GetMapping("/me") @Transactional(readOnly=true)
 UserView me(@RequestHeader(value="Authorization",required=false) String header){var user=tokens.require(header);return repo.findById(user.userId()).map(this::view).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));}
 @GetMapping("/rbac/me") @Transactional(readOnly=true)
 RbacService.Access myPermissions(@RequestHeader(value="Authorization",required=false) String token){return rbac.access(tokens.require(token).userId());}
 record RoleChange(@NotNull @Pattern(regexp="LEARNER|TEACHER|ADMIN") String role){}
 @PostMapping("/admin/users/{id}/roles") @Transactional
 RbacService.Access grantRole(@RequestHeader(value="Authorization",required=false) String header,@PathVariable UUID id,@Valid @RequestBody RoleChange change){
  if(!tokens.require(header).can("rbac:manage"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  if(!repo.existsById(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  rbac.grant(id,change.role());return rbac.access(id);
 }

}
