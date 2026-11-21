package com.toeic.interaction;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContentReviewTest {
 @Test void updateDoesNotChangeReviewIdentity() {
  UUID user=UUID.randomUUID(), content=UUID.randomUUID();
  ContentReview review=new ContentReview(user,content,3,"okay");
  UUID id=review.getId(); review.update(5,"great");
  assertEquals(id,review.getId()); assertEquals(user,review.getUserId());
  assertEquals(content,review.getContentId()); assertEquals(5,review.getStars());
 }
}
