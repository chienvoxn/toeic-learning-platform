package com.toeic.backend.questionbank.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "questions")
public class Question {
    @Id @Column(name = "question_id") private UUID questionId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "group_id") private QuestionGroup group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false) private ToeicPart part;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "topic_id") private QuestionTopic topic;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_type_id", nullable = false) private QuestionType type;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected Question() { }
    public Question(ToeicPart part, QuestionTopic topic, QuestionType type, QuestionGroup group, UUID createdBy) {
        this.questionId = UUID.randomUUID();
        this.part = QuestionValues.required(part, "part");
        this.topic = topic;
        this.type = QuestionValues.required(type, "type");
        this.group = group;
        this.createdBy = QuestionValues.required(createdBy, "createdBy");
        this.status = "DRAFT";
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }
    public UUID getQuestionId() { return questionId; }
    public QuestionGroup getGroup() { return group; }
    public ToeicPart getPart() { return part; }
    public QuestionTopic getTopic() { return topic; }
    public QuestionType getType() { return type; }
    public UUID getCreatedBy() { return createdBy; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
