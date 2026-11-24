package com.toeic.backend.assessment.domain;

import java.util.List;
import java.util.UUID;

/** DRAFT dùng version ID; thứ tự mảng là thứ tự Section/Part/câu, không chứa đáp án hoặc URL media. */
public record ExamDraftDocument(String title, String description, String testType, Integer durationSeconds,
                                List<Section> sections) {
    public ExamDraftDocument { sections = List.copyOf(sections); }
    public record Section(String type, List<Part> parts) {
        public Section { parts = List.copyOf(parts); }
    }
    public record Part(int partNumber, List<Question> questions) {
        public Part { questions = List.copyOf(questions); }
    }
    public record Question(UUID questionVersionId, UUID groupVersionId) { }
    @Override public String toString() { return "ExamDraftDocument[redacted]"; }
}
