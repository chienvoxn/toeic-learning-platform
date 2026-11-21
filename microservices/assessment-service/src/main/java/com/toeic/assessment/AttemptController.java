package com.toeic.assessment;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Entity @Table(name="attempts") class Attempt {
 @Id UUID attemptId;@Column(nullable=false) UUID userId;@Column(nullable=false) UUID quizId;@Column(nullable=false) String status="IN_PROGRESS";
 @Column(nullable=false) OffsetDateTime createdAt;Integer correctCount;OffsetDateTime gradedAt;
 protected Attempt(){}Attempt(UUID quiz,UUID user){attemptId=UUID.randomUUID();quizId=quiz;userId=user;createdAt=OffsetDateTime.now(ZoneOffset.UTC);}
}
@Entity @Table(name="attempt_questions") @IdClass(AttemptQuestionKey.class) class AttemptQuestion {
 @Id UUID attemptId;@Id UUID questionId;@Column(nullable=false,columnDefinition="text") String prompt;
 @Column(nullable=false) String correctChoice;String selectedChoice;
 @Column(name="option_a",nullable=false) String optionA;@Column(name="option_b",nullable=false) String optionB;@Column(name="option_c",nullable=false) String optionC;@Column(name="option_d",nullable=false) String optionD;
 @Column(nullable=false,columnDefinition="text") String explanation;
 protected AttemptQuestion(){}
 AttemptQuestion(UUID id,UUID qid,String prompt,String a,String b,String c,String d,String answer,String why){attemptId=id;questionId=qid;this.prompt=prompt;optionA=a;optionB=b;optionC=c;optionD=d;correctChoice=answer;explanation=why;}
}
interface AttemptRepo extends JpaRepository<Attempt,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @org.springframework.data.jpa.repository.Query("select a from Attempt a where a.attemptId=:id and a.userId=:user") Optional<Attempt> lockOwn(@Param("id") UUID id,@Param("user") UUID user);
 List<Attempt> findByUserIdOrderByCreatedAtDesc(UUID userId);
 long countByUserIdAndCreatedAtAfter(UUID user,OffsetDateTime start);
}
interface AttemptQuestionRepo extends JpaRepository<AttemptQuestion,AttemptQuestionKey>{List<AttemptQuestion> findByAttemptId(UUID id);}
@Entity @Table(name="event_outbox") class EventOutbox {
 @Id UUID eventId;@Column(nullable=false) UUID aggregateId;@Column(nullable=false) UUID actorId;@Column(nullable=false) String eventType;
 @Column(nullable=false) boolean published=false;@Column(nullable=false) OffsetDateTime occurredAt;
 protected EventOutbox(){}EventOutbox(Attempt a){eventId=UUID.randomUUID();aggregateId=a.attemptId;actorId=a.userId;eventType="AttemptGraded";occurredAt=OffsetDateTime.now(ZoneOffset.UTC);}
}
interface OutboxRepo extends JpaRepository<EventOutbox,UUID>{List<EventOutbox> findTop20ByPublishedFalseOrderByOccurredAtAsc();}
@RestController @RequestMapping("/ms/v1/attempts") class AttemptController {
 private final AttemptRepo attempts;private final AttemptQuestionRepo questions;private final OutboxRepo outbox;private final IdentityClient identity;private final QuizClient quizzes;
 AttemptController(AttemptRepo a,AttemptQuestionRepo q,OutboxRepo o,IdentityClient i,QuizClient c){attempts=a;questions=q;outbox=o;identity=i;quizzes=c;}
 record Start(@NotNull UUID quizId){}
 record Answer(@Pattern(regexp="A|B|C|D") String choice){}
 record Question(UUID id,String prompt,Map<String,String> choices,String selectedChoice,Boolean correct,String explanation){}
 record View(UUID id,UUID quizId,String status,int total,Integer correct,OffsetDateTime createdAt,List<Question> questions){}
 private List<Question> projection(UUID id,boolean graded){return questions.findByAttemptId(id).stream().map(q->new Question(q.questionId,q.prompt,Map.of("A",q.optionA,"B",q.optionB,"C",q.optionC,"D",q.optionD),q.selectedChoice,graded?q.correctChoice.equals(q.selectedChoice):null,graded?q.explanation:null)).toList();}
 private View view(Attempt a){var qs=projection(a.attemptId,"GRADED".equals(a.status));return new View(a.attemptId,a.quizId,a.status,qs.size(),a.correctCount,a.createdAt,qs);}
 private Attempt own(UUID user,UUID id){return attempts.findById(id).filter(a->a.userId.equals(user)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 @PostMapping @Transactional
 View start(@RequestHeader(value="Authorization",required=false) String bearer,@Valid @RequestBody Start input){
  var actor=identity.actor(bearer);if(!actor.can("assessment:submit"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);UUID user=actor.userId();var snapshot=quizzes.snapshot(input.quizId());var entitlement=quizzes.entitlement(user);
  if(snapshot.premium()&&!"PRO".equals(entitlement.plan()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Premium membership required");
  OffsetDateTime midnight=OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
  if(attempts.countByUserIdAndCreatedAtAfter(user,midnight)>=entitlement.dailyLimit())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Daily practice quota exceeded");
  var attempt=attempts.save(new Attempt(input.quizId(),user));
  for(var q:snapshot.questions())questions.save(new AttemptQuestion(attempt.attemptId,q.id(),q.prompt(),q.options().get("A"),q.options().get("B"),q.options().get("C"),q.options().get("D"),q.correctChoice(),q.explanation()));
  return view(attempt);
 }
 @GetMapping @Transactional(readOnly=true)
 List<View> mine(@RequestHeader(value="Authorization",required=false) String bearer){return attempts.findByUserIdOrderByCreatedAtDesc(identity.requireUser(bearer)).stream().map(this::view).toList();}
 @GetMapping("/{id}") @Transactional(readOnly=true)
 View get(@RequestHeader(value="Authorization",required=false) String bearer,@PathVariable UUID id){return view(own(identity.requireUser(bearer),id));}
 @PutMapping("/{id}/answers/{questionId}") @Transactional
 View answer(@RequestHeader(value="Authorization",required=false) String bearer,@PathVariable UUID id,@PathVariable UUID questionId,@Valid @RequestBody Answer input){
  UUID user=identity.requireUser(bearer);Attempt a=attempts.lockOwn(id,user).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(!"IN_PROGRESS".equals(a.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Already graded");
  var key=new AttemptQuestionKey();key.attemptId=id;key.questionId=questionId;
  var q=questions.findById(key).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));q.selectedChoice=input.choice();questions.save(q);return view(a);
 }
 @PostMapping("/{id}/submit") @Transactional
 View submit(@RequestHeader(value="Authorization",required=false) String bearer,@PathVariable UUID id){
  UUID user=identity.requireUser(bearer);Attempt a=attempts.lockOwn(id,user).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(!"GRADED".equals(a.status)){
   a.correctCount=(int)questions.findByAttemptId(id).stream().filter(q->q.correctChoice.equals(q.selectedChoice)).count();a.status="GRADED";a.gradedAt=OffsetDateTime.now(ZoneOffset.UTC);
   attempts.save(a);outbox.save(new EventOutbox(a));
  }
  return view(a);
 }
}
