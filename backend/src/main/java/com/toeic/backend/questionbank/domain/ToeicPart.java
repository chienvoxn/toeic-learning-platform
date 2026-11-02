package com.toeic.backend.questionbank.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "toeic_parts")
public class ToeicPart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "part_id") private Integer partId;
    @Column(name = "part_number", nullable = false, unique = true) private int partNumber;
    @Column(nullable = false, length = 20) private String skill;
    @Column(nullable = false, length = 100) private String name;

    protected ToeicPart() { }
    public ToeicPart(int partNumber, String skill, String name) {
        this.partNumber = QuestionValues.positive(partNumber, "partNumber");
        this.skill = QuestionValues.text(skill, 20, "skill");
        this.name = QuestionValues.text(name, 100, "name");
    }
    public Integer getPartId() { return partId; }
    public int getPartNumber() { return partNumber; }
    public String getSkill() { return skill; }
    public String getName() { return name; }
}
