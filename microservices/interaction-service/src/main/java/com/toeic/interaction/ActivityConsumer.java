package com.toeic.interaction;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.*;
@Entity @Table(name="learning_activities") class LearningActivity {
 @Id UUID eventId;@Column(nullable=false) UUID userId;@Column(nullable=false) UUID attemptId;
 @Column(nullable=false) String kind;@Column(nullable=false) OffsetDateTime recordedAt;
 protected LearningActivity(){}LearningActivity(UUID eventId,UUID userId,UUID attemptId,String kind){this.eventId=eventId;this.userId=userId;this.attemptId=attemptId;this.kind=kind;recordedAt=OffsetDateTime.now(ZoneOffset.UTC);}
 public UUID getEventId(){return eventId;}public UUID getUserId(){return userId;}public UUID getAttemptId(){return attemptId;}public String getKind(){return kind;}public OffsetDateTime getRecordedAt(){return recordedAt;}
}
interface ActivityRepo extends JpaRepository<LearningActivity,UUID>{List<LearningActivity> findByUserIdOrderByRecordedAtDesc(UUID userId);}
@Configuration class ActivityMessaging {
 @Bean TopicExchange toeicEvents(){return new TopicExchange("toeic.events",true,false);}
 @Bean org.springframework.amqp.core.Queue activityQueue(){return QueueBuilder.durable("interaction.attempt-graded").build();}
 @Bean Binding activityBinding(org.springframework.amqp.core.Queue activityQueue,TopicExchange toeicEvents){return BindingBuilder.bind(activityQueue).to(toeicEvents).with("attempt.graded");}
}
@Component class ActivityConsumer {
 private final ActivityRepo repo; private final ObjectMapper json;ActivityConsumer(ActivityRepo repo,ObjectMapper json){this.repo=repo;this.json=json;}
 @RabbitListener(queues="interaction.attempt-graded") @Transactional
 public void handle(String payload) throws Exception {var event=json.readTree(payload);UUID id=UUID.fromString(event.path("eventId").asText());
  if(!repo.existsById(id))repo.save(new LearningActivity(id,UUID.fromString(event.path("userId").asText()),UUID.fromString(event.path("attemptId").asText()),"ATTEMPT_GRADED"));}
}
@RestController @RequestMapping("/api/v1/interactions/activities") class ActivityController {
 private final ActivityRepo repo;private final IdentityClient identity;
 ActivityController(ActivityRepo repo,IdentityClient identity){this.repo=repo;this.identity=identity;}
 @GetMapping("/me") @Transactional(readOnly=true)
 List<LearningActivity> mine(@RequestHeader(value="Authorization",required=false) String bearer){return repo.findByUserIdOrderByRecordedAtDesc(identity.requireUser(bearer));}
}
