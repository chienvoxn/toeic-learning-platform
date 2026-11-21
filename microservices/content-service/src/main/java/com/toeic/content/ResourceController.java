package com.toeic.content;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Entity @Table(name="learning_resources") class LearningResource {
 @Id UUID resourceId; @Column(nullable=false) UUID ownerUserId; @Column(nullable=false) String title;
 @Column(nullable=false,columnDefinition="text") String body; @Column(nullable=false) String visibility;
 @Version Integer version=0;
 protected LearningResource(){}
 LearningResource(UUID owner,String title,String body){resourceId=UUID.randomUUID();ownerUserId=owner;this.title=title;this.body=body;visibility="PRIVATE";}
}
interface ResourceRepository extends JpaRepository<LearningResource,UUID>{List<LearningResource> findByOwnerUserId(UUID userId);}
@RestController @RequestMapping("/ms/v1/contents") class ResourceController {
 private final ResourceRepository repo;private final IdentityClient auth;
 ResourceController(ResourceRepository repo,IdentityClient auth){this.repo=repo;this.auth=auth;}
 record Create(@NotBlank @Size(max=240) String title,@NotBlank String body){}
 record View(UUID id,UUID ownerId,String title,String body,String visibility){}
 View view(LearningResource r){return new View(r.resourceId,r.ownerUserId,r.title,r.body,r.visibility);}
 @PostMapping @Transactional
 View create(@RequestHeader(value="Authorization",required=false) String token,@Valid @RequestBody Create cmd){
  var actor=auth.actor(token);if(!actor.can("content:write"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);UUID owner=actor.userId();return view(repo.save(new LearningResource(owner,cmd.title(),cmd.body())));
 }
 @GetMapping("/{id}") @Transactional(readOnly=true)
 View get(@RequestHeader(value="Authorization",required=false) String token,@PathVariable UUID id){
  UUID user=auth.requireUser(token);var r=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(!r.ownerUserId.equals(user)&&!"PUBLIC".equals(r.visibility))throw new ResponseStatusException(HttpStatus.NOT_FOUND);return view(r);
 }
 @GetMapping("/mine") @Transactional(readOnly=true)
 List<View> mine(@RequestHeader(value="Authorization",required=false) String token){return repo.findByOwnerUserId(auth.requireUser(token)).stream().map(this::view).toList();}
}
