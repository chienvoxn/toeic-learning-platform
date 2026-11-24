package com.toeic.backend.assessment.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Practice giữ pin bất biến; trạng thái/kết quả chỉ chốt khi caller giữ khóa attempt. */
@Entity
@Table(name = "attempts")
public class PracticeAttempt {
    @Id @Column(name = "attempt_id") private UUID attemptId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "test_version_id") private UUID testVersionId;
    @Column(name = "attempt_type", nullable = false) private String attemptType;
    @Column(name = "attempt_source_type", nullable = false) private String sourceType;
    @Column(nullable = false) private String status;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "total_questions") private Integer totalQuestions;
    @Column(name = "submitted_at") private OffsetDateTime submittedAt;
    @Column(name = "duration_seconds") private Integer durationSeconds;
    @Column(name = "correct_count") private Integer correctCount;
    @Column(name = "listening_correct_count") private Integer listeningCorrectCount;
    @Column(name = "reading_correct_count") private Integer readingCorrectCount;
    @Column(name = "section_type") private String sectionType;
    @Column(name = "configured_duration_seconds") private Integer configuredDurationSeconds;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "test_blueprint_snapshot",columnDefinition="jsonb") private TestAttemptSnapshot testSnapshot;
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.PERSIST)
    @OrderBy("questionOrder ASC") private List<PracticeQuestionSnapshot> questions = new ArrayList<>();

    protected PracticeAttempt() { }
    public record Pin(UUID questionVersionId, UUID groupVersionId) {
        public Pin { Objects.requireNonNull(questionVersionId, "questionVersionId"); }
    }
    public PracticeAttempt(UUID actor, OffsetDateTime started, OffsetDateTime expiry, List<Pin> pins) {
        userId = Objects.requireNonNull(actor, "actor");
        startedAt = Objects.requireNonNull(started, "started");
        expiresAt = Objects.requireNonNull(expiry, "expiry");
        if (!expiry.isAfter(started) || pins == null || pins.isEmpty() || pins.stream().anyMatch(Objects::isNull)
                || pins.stream().map(Pin::questionVersionId).distinct().count() != pins.size()) {
            throw new IllegalArgumentException("Invalid practice snapshot");
        }
        attemptId = UUID.randomUUID();
        attemptType = "PRACTICE";
        sourceType = "PRACTICE_SNAPSHOT";
        status = "IN_PROGRESS";
        createdAt = started;
        totalQuestions = pins.size();
        for (int i = 0; i < pins.size(); i++) {
            questions.add(new PracticeQuestionSnapshot(this, pins.get(i), i + 1, started));
        }
    }
    public String getAttemptType() { return attemptType; }
    /** Chỉ đọc dữ liệu từ factory FULL_TEST của develop; không tạo phiên fixed 200/120 mới. */
    public boolean isLegacyFullTest() {
        if(!"TEST_VERSION".equals(sourceType)||!"FULL_TEST".equals(attemptType)||testSnapshot!=null||testVersionId==null
                ||!Objects.equals(totalQuestions,200)||startedAt==null||expiresAt==null
                ||!expiresAt.toInstant().equals(startedAt.plusMinutes(120).toInstant())||questions.size()!=200)return false;
        var seen=new HashSet<UUID>();int order=0;
        for(var pin:questions)if(pin.getQuestionVersionId()==null||!seen.add(pin.getQuestionVersionId())||pin.getQuestionOrder()!=++order)return false;
        return true;
    }
    public boolean isFullTest() { return isTestVersion() || isLegacyFullTest(); }
    public UUID getAttemptId() { return attemptId; }
    public static PracticeAttempt section(UUID actor,OffsetDateTime started,String type,int minutes,List<Pin> pins) {
        if(type==null||!Set.of("LISTENING","READING").contains(type)||minutes<1||minutes>120||pins==null||pins.size()>100) {
            throw new IllegalArgumentException("Invalid Section configuration");
        }
        var attempt=new PracticeAttempt(actor,started,started.plusMinutes(minutes),pins);
        attempt.attemptType="SECTION";attempt.sourceType="SECTION_SNAPSHOT";
        attempt.sectionType=type;attempt.configuredDurationSeconds=minutes*60;
        return attempt;
    }
    public boolean isSection() { return "SECTION_SNAPSHOT".equals(sourceType); }
    public boolean isTestVersion() { return "TEST_VERSION".equals(sourceType) && testSnapshot!=null; }
    public boolean isTimed() { return isSection() || isTestVersion(); }
    public TestAttemptSnapshot getTestSnapshot() { return testSnapshot; }
    public static PracticeAttempt fullMock(UUID actor,OffsetDateTime started,TestAttemptSnapshot snapshot,List<Pin> pins) {
        Objects.requireNonNull(snapshot);Objects.requireNonNull(snapshot.blueprint());
        if(snapshot.versionId()==null||snapshot.blueprint().durationSeconds()<1||snapshot.blueprint().totalQuestions()!=pins.size())throw new IllegalArgumentException("Invalid Full Mock snapshot");
        var attempt=new PracticeAttempt(actor,started,started.plusSeconds(snapshot.blueprint().durationSeconds()),pins);
        attempt.attemptType="FULL_MOCK";attempt.sourceType="TEST_VERSION";attempt.testVersionId=snapshot.versionId();attempt.testSnapshot=snapshot;
        return attempt;
    }
    public String getSectionType() { return sectionType; }
    public Integer getConfiguredDurationSeconds() { return configuredDurationSeconds; }
    public UUID getUserId() { return userId; }
    public UUID getTestVersionId() { return testVersionId; }
    public String getSourceType() { return sourceType; }
    public String getStatus() { return status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public Integer getTotalQuestions() { return totalQuestions; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public Integer getCorrectCount() { return correctCount; }
    public Integer getListeningCorrectCount() { return listeningCorrectCount; }
    public Integer getReadingCorrectCount() { return readingCorrectCount; }
    public void completeGrading(OffsetDateTime submitted, int correct, int listening, int reading) {
        if (!"IN_PROGRESS".equals(status) || submitted.isBefore(startedAt) || !isTimed() && !submitted.isBefore(expiresAt)
                || correct < 0 || correct > totalQuestions || listening < 0 || reading < 0 || listening + reading != correct) {
            throw new IllegalStateException("Invalid practice grading transition");
        }
        submittedAt = submitted;
        var elapsedUntil=isTimed()&&submitted.isAfter(expiresAt)?expiresAt:submitted;
        durationSeconds = Math.toIntExact(java.time.temporal.ChronoUnit.SECONDS.between(startedAt, elapsedUntil));
        correctCount = correct;
        listeningCorrectCount = listening;
        readingCorrectCount = reading;
        status = "GRADED";
    }
    public List<PracticeQuestionSnapshot> getQuestions() { return List.copyOf(questions); }
}
