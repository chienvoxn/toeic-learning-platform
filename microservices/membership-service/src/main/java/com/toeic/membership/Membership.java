package com.toeic.membership;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="user_memberships") public class Membership {
 @Id private UUID id;
 @Column(name="user_id",nullable=false,unique=true) private UUID userId;
 @Column(name="plan_code",nullable=false) private String planCode;
 @Column(nullable=false) private String status;
 @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
 protected Membership(){}
 public Membership(UUID userId,String planCode){this.id=UUID.randomUUID();this.userId=userId;this.planCode=planCode;this.status="ACTIVE";this.updatedAt=OffsetDateTime.now();}
 public UUID getUserId(){return userId;}
 public String getPlanCode(){return planCode;}
 public String getStatus(){return status;}
 public void changePlan(String code){planCode=code;updatedAt=OffsetDateTime.now();}
}
