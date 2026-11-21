package com.toeic.interaction;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="content_reviews") public class ContentReview {
 @Id private UUID id;
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(name="content_id",nullable=false) private UUID contentId;
 @Column(nullable=false) private int stars;
 @Column(length=1000) private String comment;
 @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;
 @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
 protected ContentReview(){}
 public ContentReview(UUID userId,UUID contentId,int stars,String comment){id=UUID.randomUUID();this.userId=userId;this.contentId=contentId;update(stars,comment);createdAt=updatedAt;}
 public void update(int stars,String comment){this.stars=stars;this.comment=comment;updatedAt=OffsetDateTime.now();}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public UUID getContentId(){return contentId;} public int getStars(){return stars;}public String getComment(){return comment;}public OffsetDateTime getCreatedAt(){return createdAt;}
}
