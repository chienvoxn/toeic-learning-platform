package com.toeic.interaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/interactions") public class InteractionController {
 private final IdentityClient identity;private final ContentReviewRepository repository;
 public InteractionController(IdentityClient identity,ContentReviewRepository repository){this.identity=identity;this.repository=repository;}
 public record ReviewRequest(@Min(1) @Max(5) int stars,@Size(max=1000) String comment){}
 @GetMapping("/contents/{contentId}/reviews") @Transactional(readOnly=true)
 public List<ContentReview> reviews(@PathVariable UUID contentId){return repository.findTop50ByContentIdOrderByCreatedAtDesc(contentId);}
 @PutMapping("/contents/{contentId}/reviews/me") @Transactional
 public ContentReview upsert(@PathVariable UUID contentId,@RequestHeader(value="Authorization",required=false) String bearer,@Valid @RequestBody ReviewRequest data){
  var actor=identity.actor(bearer);if(!actor.can("interaction:write"))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);UUID user=actor.userId();
  var entity=repository.findByUserIdAndContentId(user,contentId).orElseGet(()->new ContentReview(user,contentId,data.stars(),data.comment()));
  entity.update(data.stars(),data.comment());return repository.save(entity);
 }
}
