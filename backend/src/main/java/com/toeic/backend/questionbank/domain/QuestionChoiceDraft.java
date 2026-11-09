package com.toeic.backend.questionbank.domain;

/** Structural choice input only. Type/Part answer policy belongs to the authoring use case. */
public record QuestionChoiceDraft(String label, String content, boolean correct, int displayOrder) {
    public QuestionChoiceDraft {
        label = QuestionValues.text(label, 10, "choice label");
        content = QuestionValues.text(content, Integer.MAX_VALUE, "choice content");
        QuestionValues.positive(displayOrder, "choice displayOrder");
    }

    @Override
    public String toString() { return "QuestionChoiceDraft[redacted]"; }
}
