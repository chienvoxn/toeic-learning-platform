package com.toeic.backend.questionbank.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "question_types")
public class QuestionType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_type_id") private Integer questionTypeId;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(columnDefinition = "TEXT") private String description;

    protected QuestionType() { }
    public QuestionType(String code, String name, String description) {
        this.code = QuestionValues.text(code, 50, "type code");
        this.name = QuestionValues.text(name, 100, "type name");
        this.description = description;
    }
    public Integer getQuestionTypeId() { return questionTypeId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
