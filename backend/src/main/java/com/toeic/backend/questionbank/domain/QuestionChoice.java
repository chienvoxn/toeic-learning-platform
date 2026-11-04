package com.toeic.backend.questionbank.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "question_choices")
public class QuestionChoice {
    @Id @Column(name = "choice_id") private UUID choiceId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_version_id", nullable = false) private QuestionVersion version;
    @Column(name = "choice_label", nullable = false, length = 10) private String label;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "is_correct", nullable = false) private boolean correct;
    @Column(name = "display_order", nullable = false) private int displayOrder;

    protected QuestionChoice() { }
    QuestionChoice(QuestionVersion version, String label, String content, boolean correct, int displayOrder) {
        this.choiceId = UUID.randomUUID();
        this.version = version;
        this.label = QuestionValues.text(label, 10, "choice label");
        this.content = QuestionValues.text(content, Integer.MAX_VALUE, "choice content");
        this.correct = correct;
        this.displayOrder = QuestionValues.positive(displayOrder, "choice displayOrder");
    }
    public UUID getChoiceId() { return choiceId; }
    public UUID getQuestionVersionId() { return version.getQuestionVersionId(); }
    public String getLabel() { return label; }
    public String getContent() { return content; }
    @JsonIgnore public boolean isCorrect() { return correct; }
    public int getDisplayOrder() { return displayOrder; }
}
