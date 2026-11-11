package com.toeic.backend.questionbank.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "topics")
public class QuestionTopic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "topic_id") private Integer topicId;
    @Column(nullable = false, unique = true, length = 100) private String name;
    @Column(columnDefinition = "TEXT") private String description;

    protected QuestionTopic() { }
    public QuestionTopic(String name, String description) {
        this.name = QuestionValues.text(name, 100, "topic name");
        this.description = description;
    }
    public Integer getTopicId() { return topicId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
