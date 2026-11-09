package com.toeic.backend.questionbank.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "question_versions")
public class QuestionVersion {
    @Id @Column(name = "question_version_id") private UUID questionVersionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false) private Question question;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "group_version_id")
    private QuestionGroupVersion groupVersion;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT") private String questionText;
    @Column(nullable = false, length = 20) private String status;
    @Column(columnDefinition = "TEXT") private String explanation;
    @Column(length = 20) private String difficulty;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private OffsetDateTime approvedAt;
    @Column(name = "published_at") private OffsetDateTime publishedAt;
    @Column(name = "archived_at") private OffsetDateTime archivedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @OneToMany(mappedBy = "version", cascade = CascadeType.PERSIST)
    @OrderBy("displayOrder ASC") private List<QuestionChoice> choices = new ArrayList<>();
    @OneToMany(mappedBy = "version", cascade = CascadeType.PERSIST)
    private Set<QuestionResource> resources = new HashSet<>();

    protected QuestionVersion() { }
    public QuestionVersion(Question question, int versionNumber, QuestionGroupVersion groupVersion,
                           String text, String explanation, String difficulty) {
        this.questionVersionId = UUID.randomUUID();
        this.question = QuestionValues.required(question, "question");
        this.versionNumber = QuestionValues.positive(versionNumber, "versionNumber");
        this.groupVersion = groupVersion;
        this.status = "DRAFT";
        reviseDraft(text, explanation, difficulty);
        verifyReferences();
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
    public void verifyReferences() {
        var expected = question.getGroup();
        if ((expected == null) != (groupVersion == null)
                || expected != null && !expected.getGroupId().equals(groupVersion.getGroup().getGroupId())) {
            throw new IllegalArgumentException("Question/group version mismatch");
        }
    }
    private void requireDraft() {
        if (!"DRAFT".equals(status)) { throw new IllegalStateException("Only DRAFT question versions can change"); }
    }
    public void reviseDraft(String text, String explanation, String difficulty) {
        requireDraft();
        var validatedText = QuestionValues.text(text, Integer.MAX_VALUE, "question text");
        var validatedDifficulty = QuestionValues.optional(difficulty, 20, "difficulty");
        this.questionText = validatedText;
        this.explanation = explanation;
        this.difficulty = validatedDifficulty;
    }
    public QuestionChoice addChoice(String label, String content, boolean correct, int order) {
        requireDraft();
        if (choices.stream().anyMatch(choice -> choice.getLabel().equals(label) || choice.getDisplayOrder() == order)) {
            throw new IllegalArgumentException("Duplicate choice label/order in version");
        }
        var choice = new QuestionChoice(this, label, content, correct, order);
        choices.add(choice);
        return choice;
    }
    /** Validate the whole structural batch before mutating this draft. No publish-time policy is inferred. */
    public List<QuestionChoice> addChoices(List<QuestionChoiceDraft> input) {
        requireDraft();
        if (input == null || input.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Invalid choice batch");
        }
        var labels = choices.stream().map(QuestionChoice::getLabel).collect(Collectors.toCollection(HashSet::new));
        var orders = choices.stream().map(QuestionChoice::getDisplayOrder).collect(Collectors.toCollection(HashSet::new));
        var additions = new ArrayList<QuestionChoice>();
        for (var item : input) {
            if (!labels.add(item.label()) || !orders.add(item.displayOrder())) {
                throw new IllegalArgumentException("Duplicate choice label/order in version");
            }
            additions.add(new QuestionChoice(this, item.label(), item.content(), item.correct(), item.displayOrder()));
        }
        choices.addAll(additions);
        return List.copyOf(additions);
    }
    public void addResource(UUID resourceId) {
        requireDraft();
        if (resources.stream().anyMatch(resource -> resource.getResourceId().equals(resourceId))) {
            throw new IllegalArgumentException("Duplicate question resource");
        }
        resources.add(new QuestionResource(this, resourceId));
    }
    @JsonIgnore public QuestionAnswerKey answerKey() {
        return new QuestionAnswerKey(questionVersionId,
                choices.stream().filter(QuestionChoice::isCorrect).map(QuestionChoice::getChoiceId).collect(Collectors.toSet()),
                explanation);
    }
    public UUID getQuestionVersionId() { return questionVersionId; }
    public Question getQuestion() { return question; }
    public QuestionGroupVersion getGroupVersion() { return groupVersion; }
    public int getVersionNumber() { return versionNumber; }
    public String getQuestionText() { return questionText; }
    public String getStatus() { return status; }
    @JsonIgnore public String getExplanation() { return explanation; }
    public String getDifficulty() { return difficulty; }
    public UUID getReviewedBy() { return reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<QuestionChoice> getChoices() {
        return choices.stream().sorted(Comparator.comparingInt(QuestionChoice::getDisplayOrder)).toList();
    }
    public List<QuestionResource> getResources() {
        return resources.stream().sorted(Comparator.comparing(resource -> resource.getResourceId().toString())).toList();
    }
}
