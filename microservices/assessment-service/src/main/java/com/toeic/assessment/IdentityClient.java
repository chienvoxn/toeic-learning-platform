package com.toeic.assessment;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component public class IdentityClient {
 private final TokenService tokens;
 public IdentityClient(TokenService tokens){this.tokens=tokens;}
 public UUID requireUser(String header){return tokens.require(header).userId();}
 public TokenService.Actor actor(String header){return tokens.require(header);}
}
