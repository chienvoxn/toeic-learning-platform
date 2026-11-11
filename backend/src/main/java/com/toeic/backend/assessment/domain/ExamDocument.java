package com.toeic.backend.assessment.domain;

import java.util.List;
import java.util.UUID;

/** Định dạng import và bản nháp; thứ tự mảng questions chính là thứ tự câu thi. */
public record ExamDocument(String title, String description, String testType, Integer durationSeconds,
        Integer maxAttempts, UUID conversionId, List<Group> groups, List<Question> questions) {
    @Override public String toString(){return "ExamDocument[redacted]";}
    public record Group(String key, int partNumber, String instruction, String passageText,
            UUID audioId, UUID imageId, String transcript) {}
    public record Question(int partNumber, String groupKey, String questionText, List<String> choices,
            String answer, String explanation, UUID audioId, UUID imageId, String transcript) {}
}
