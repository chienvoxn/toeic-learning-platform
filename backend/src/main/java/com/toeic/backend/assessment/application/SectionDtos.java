package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import java.math.BigDecimal;
import java.util.*;

/** DTO allowlist cho cấu hình đã pin và thống kê Section sau submit. */
public final class SectionDtos {
    private SectionDtos() { }
    public record Configuration(String type,int questionCount,int durationMinutes,List<Integer> partNumbers) {
        public Configuration { partNumbers=List.copyOf(partNumbers); }
    }
    public record PartResult(int partNumber,int totalQuestions,int correctCount,int incorrectCount,int unansweredCount,
            int rawScore,BigDecimal accuracy) { }
    static Configuration configuration(PracticeAttempt attempt) {
        if(!attempt.isSection())return null;
        var query=new SectionStartQuery(attempt.getSectionType(),attempt.getTotalQuestions(),attempt.getConfiguredDurationSeconds()/60);
        return new Configuration(query.type(),query.questionCount(),query.durationMinutes(),query.partNumbers());
    }
}
