package com.toeic.backend.assessment.application;

import com.toeic.backend.questionbank.application.PracticeQuestionViewMapper;
import com.toeic.backend.questionbank.application.QuestionPrompt;
import java.time.OffsetDateTime;
import java.util.*;

public final class PracticeSessionDtos {
    private PracticeSessionDtos() { }
    public record Question(int order,UUID questionId,UUID questionVersionId,int versionNumber,int partNumber,
            String topic,String questionType,String difficulty,String questionText,List<QuestionPrompt.Choice> choices,
            List<PracticeQuestionViewMapper.Asset> assets,PracticeQuestionViewMapper.Passage passage) {
        public Question { choices=List.copyOf(choices);assets=List.copyOf(assets); }
        public static Question from(int order,PracticeQuestionViewMapper.View v) {
            return new Question(order,v.questionId(),v.questionVersionId(),v.versionNumber(),v.partNumber(),v.topic(),
                    v.questionType(),v.difficulty(),v.questionText(),v.choices(),v.assets(),v.passage());
        }
    }
    public record Session(UUID attemptId,String sourceType,String status,OffsetDateTime startedAt,OffsetDateTime expiresAt,
            boolean expired,int totalQuestions,List<Question> questions) {
        public Session { questions=List.copyOf(questions); }
    }
}
