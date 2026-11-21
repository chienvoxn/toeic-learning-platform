package com.toeic.content;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Entity @Table(name="toeic_quizzes") class Quiz {
 @Id UUID quizId;@Column(nullable=false) String title;@Column(nullable=false) int part;@Column(nullable=false) boolean premium;@Column(nullable=false) boolean published;
 protected Quiz(){} Quiz(String title,int part,boolean premium){quizId=UUID.randomUUID();this.title=title;this.part=part;this.premium=premium;this.published=true;}
}
@Entity @Table(name="toeic_questions") class QuizQuestion {
 @Id UUID questionId;@Column(nullable=false) UUID quizId;@Column(nullable=false,columnDefinition="text") String prompt;
 @Column(name="option_a",nullable=false) String optionA;@Column(name="option_b",nullable=false) String optionB;@Column(name="option_c",nullable=false) String optionC;@Column(name="option_d",nullable=false) String optionD;
 @Column(nullable=false) String correctChoice;@Column(nullable=false,columnDefinition="text") String explanation;
 protected QuizQuestion(){} QuizQuestion(UUID quiz,String prompt,String a,String b,String c,String d,String correct,String explanation){questionId=UUID.randomUUID();quizId=quiz;this.prompt=prompt;optionA=a;optionB=b;optionC=c;optionD=d;correctChoice=correct;this.explanation=explanation;}
}
interface QuizRepo extends JpaRepository<Quiz,UUID>{List<Quiz> findByPublishedTrueOrderByPartAsc();}
interface QuizQuestionRepo extends JpaRepository<QuizQuestion,UUID>{List<QuizQuestion> findByQuizIdOrderByQuestionIdAsc(UUID quizId);}
@RestController @RequestMapping("/ms/v1/contents/quizzes") class QuizController {
 private final QuizRepo quizzes;private final QuizQuestionRepo questions;private final IdentityClient identity;
 @Value("${services.internal-key}") String key;
 QuizController(QuizRepo q,QuizQuestionRepo s,IdentityClient a){quizzes=q;questions=s;identity=a;}
 record QuizView(UUID id,String title,int part,boolean premium,int count){}
 record PublicQuestion(UUID id,String prompt,Map<String,String> choices){}
 record QuizDetail(QuizView quiz,List<PublicQuestion> questions){}
 record InternalQuestion(UUID id,String prompt,Map<String,String> choices,String correctChoice,String explanation){}
 record Snapshot(QuizView quiz,List<InternalQuestion> questions){}
 record NewQuestion(@NotBlank String prompt,@NotBlank String a,@NotBlank String b,@NotBlank String c,@NotBlank String d,@Pattern(regexp="A|B|C|D") String answer,@NotBlank String explanation){}
 record NewQuiz(@NotBlank String title,@Min(1) @Max(7) int part,boolean premium,@NotEmpty List<@Valid NewQuestion> questions){}
 Quiz getPublished(UUID id){return quizzes.findById(id).filter(q->q.published).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 QuizView view(Quiz quiz){return new QuizView(quiz.quizId,quiz.title,quiz.part,quiz.premium,questions.findByQuizIdOrderByQuestionIdAsc(quiz.quizId).size());}
 Map<String,String> options(QuizQuestion q){return Map.of("A",q.optionA,"B",q.optionB,"C",q.optionC,"D",q.optionD);}
 @GetMapping @Transactional(readOnly=true)
 List<QuizView> list(){return quizzes.findByPublishedTrueOrderByPartAsc().stream().map(this::view).toList();}
 @GetMapping("/{id}") @Transactional(readOnly=true)
 QuizDetail detail(@PathVariable UUID id){var quiz=getPublished(id);return new QuizDetail(view(quiz),questions.findByQuizIdOrderByQuestionIdAsc(id).stream().map(q->new PublicQuestion(q.questionId,q.prompt,options(q))).toList());}
 @GetMapping("/internal/{id}/snapshot") @Transactional(readOnly=true)
 Snapshot internal(@PathVariable UUID id,@RequestHeader(value="X-Service-Key",required=false) String secret){
  if(secret==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  var q=getPublished(id);return new Snapshot(view(q),questions.findByQuizIdOrderByQuestionIdAsc(id).stream().map(x->new InternalQuestion(x.questionId,x.prompt,options(x),x.correctChoice,x.explanation)).toList());
 }
 @PostMapping @Transactional
 QuizView create(@RequestHeader(value="Authorization",required=false) String bearer,@Valid @RequestBody NewQuiz input){
  if(!identity.actor(bearer).can("content:write"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  var quiz=quizzes.save(new Quiz(input.title(),input.part(),input.premium()));
  for(var q:input.questions())questions.save(new QuizQuestion(quiz.quizId,q.prompt(),q.a(),q.b(),q.c(),q.d(),q.answer(),q.explanation()));return view(quiz);
 }
}
