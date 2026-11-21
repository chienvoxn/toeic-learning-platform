package com.toeic.assessment;
import java.util.*;
public class AttemptQuestionKey implements java.io.Serializable {
 public UUID attemptId;public UUID questionId;public AttemptQuestionKey(){}
 @Override public boolean equals(Object o){return o instanceof AttemptQuestionKey k && Objects.equals(attemptId,k.attemptId)&&Objects.equals(questionId,k.questionId);}
 @Override public int hashCode(){return Objects.hash(attemptId,questionId);}
}
