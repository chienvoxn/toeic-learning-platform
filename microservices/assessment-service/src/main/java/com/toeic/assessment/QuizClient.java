package com.toeic.assessment;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
@Component public class QuizClient {
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 private final ObjectMapper json;private final String content,membership,key;
 QuizClient(ObjectMapper json,@Value("${services.content-url}") String content,@Value("${services.membership-url}") String membership,@Value("${services.internal-key}") String key){this.json=json;this.content=content;this.membership=membership;this.key=key;}
 record QuizQuestion(UUID id,String prompt,Map<String,String> options,String correctChoice,String explanation){}
 record Snapshot(UUID quizId,boolean premium,List<QuizQuestion> questions){}
 record Entitlement(String plan,int dailyLimit){}
 private JsonNode request(String url) {
  try {var req=HttpRequest.newBuilder(URI.create(url)).header("X-Service-Key",key).timeout(Duration.ofSeconds(20)).GET().build();var r=http.send(req,HttpResponse.BodyHandlers.ofString());
   if(r.statusCode()==404)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Quiz not found");
   if(r.statusCode()!=200){System.err.println("Dependency HTTP "+r.statusCode()+" path="+URI.create(url).getPath());throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Dependency not available");}return json.readTree(r.body());
  }catch(ResponseStatusException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);}catch(Exception e){System.err.println("Dependency exception: "+e.getClass().getName()+" - "+e.getMessage());throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Dependency not available",e);}
 }
 Snapshot snapshot(UUID quizId){JsonNode body=request(content+"/ms/v1/contents/quizzes/internal/"+quizId+"/snapshot");var qs=new ArrayList<QuizQuestion>();
  for(JsonNode q:body.path("questions")){Map<String,String> opts=new HashMap<>();for(String option:List.of("A","B","C","D"))opts.put(option,q.path("choices").path(option).asText());qs.add(new QuizQuestion(UUID.fromString(q.path("id").asText()),q.path("prompt").asText(),opts,q.path("correctChoice").asText(),q.path("explanation").asText()));}
  if(qs.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Empty quiz");return new Snapshot(quizId,body.path("quiz").path("premium").asBoolean(),qs);
 }
 Entitlement entitlement(UUID user){JsonNode body=request(membership+"/api/v1/memberships/internal/"+user);return new Entitlement(body.path("planCode").asText("FREE"),body.path("maxPracticePerDay").asInt(10));}
}
