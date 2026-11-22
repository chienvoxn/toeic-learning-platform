
package com.toeic.backend.assessment.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Attempt giữ các phiên bản câu hỏi cố định.
 * Trạng thái và kết quả chỉ được cập nhật khi caller giữ khóa attempt.
 */
@Entity
@Table(name = "attempts")
public class PracticeAttempt {

    @Id
    @Column(name = "attempt_id")
    private UUID attemptId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "test_version_id")
    private UUID testVersionId;

    @Column(name = "attempt_type", nullable = false)
    private String attemptType;

    @Column(name = "attempt_source_type", nullable = false)
    private String sourceType;

    @Column(nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "listening_correct_count")
    private Integer listeningCorrectCount;

    @Column(name = "reading_correct_count")
    private Integer readingCorrectCount;

    @Column(name = "section_type")
    private String sectionType;

    @Column(name = "configured_duration_seconds")
    private Integer configuredDurationSeconds;

    @OneToMany(
        mappedBy = "attempt",
        cascade = CascadeType.PERSIST
    )
    @OrderBy("questionOrder ASC")
    private List<PracticeQuestionSnapshot> questions =
            new ArrayList<>();

    protected PracticeAttempt() {
    }

    public record Pin(
            UUID questionVersionId,
            UUID groupVersionId
    ) {
        public Pin {
            Objects.requireNonNull(
                questionVersionId,
                "questionVersionId"
            );
        }
    }

    // =====================================================
    // 1. PRACTICE ATTEMPT
    // =====================================================

    public PracticeAttempt(
            UUID actor,
            OffsetDateTime started,
            OffsetDateTime expiry,
            List<Pin> pins
    ) {
        userId = Objects.requireNonNull(actor, "actor");
        startedAt = Objects.requireNonNull(started, "started");
        expiresAt = Objects.requireNonNull(expiry, "expiry");

        if (!expiry.isAfter(started)
                || pins == null
                || pins.isEmpty()
                || pins.stream().anyMatch(Objects::isNull)
                || pins.stream()
                    .map(Pin::questionVersionId)
                    .distinct()
                    .count() != pins.size()) {

            throw new IllegalArgumentException(
                "Invalid practice snapshot"
            );
        }

        attemptId = UUID.randomUUID();

        attemptType = "PRACTICE";
        sourceType = "PRACTICE_SNAPSHOT";
        status = "IN_PROGRESS";

        createdAt = started;
        totalQuestions = pins.size();

        for (int i = 0; i < pins.size(); i++) {
            questions.add(
                new PracticeQuestionSnapshot(
                    this,
                    pins.get(i),
                    i + 1,
                    started
                )
            );
        }
    }

    // =====================================================
    // 2. SECTION ATTEMPT
    // =====================================================

    public static PracticeAttempt section(
            UUID actor,
            OffsetDateTime started,
            String type,
            int minutes,
            List<Pin> pins
    ) {
        if (type == null
                || !Set.of("LISTENING", "READING").contains(type)
                || minutes < 1
                || minutes > 120
                || pins == null
                || pins.size() > 100) {

            throw new IllegalArgumentException(
                "Invalid Section configuration"
            );
        }

        var attempt = new PracticeAttempt(
            actor,
            started,
            started.plusMinutes(minutes),
            pins
        );

        attempt.attemptType = "SECTION";
        attempt.sourceType = "SECTION_SNAPSHOT";
        attempt.sectionType = type;
        attempt.configuredDurationSeconds = minutes * 60;

        return attempt;
    }

    // =====================================================
    // 3. FULL TOEIC TEST ATTEMPT - NEW
    // =====================================================

    /**
     * Tạo Full TOEIC Listening & Reading Test.
     *
     * - 200 câu hỏi cố định
     * - Thời gian 120 phút
     * - Gắn với testVersionId đã Publish
     * - Giữ thứ tự câu hỏi theo danh sách pins
     *
     * Việc xác minh đề PUBLISHED được thực hiện
     * bởi FullTestSessionService.
     */
    public static PracticeAttempt fullTest(
            UUID actor,
            UUID testVersionId,
            OffsetDateTime started,
            List<Pin> pins
    ) {
        Objects.requireNonNull(
            testVersionId,
            "testVersionId"
        );

        if (pins == null || pins.size() != 200) {
            throw new IllegalArgumentException(
                "Full TOEIC Test requires exactly 200 questions"
            );
        }

        var attempt = new PracticeAttempt(
            actor,
            started,
            started.plusMinutes(120),
            pins
        );

        attempt.testVersionId = testVersionId;
        attempt.attemptType = "FULL_TEST";
        attempt.sourceType = "TEST_VERSION";

        return attempt;
    }

    // =====================================================
    // 4. GETTERS
    // =====================================================

    public UUID getAttemptId() {
        return attemptId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTestVersionId() {
        return testVersionId;
    }

    public String getAttemptType() {
        return attemptType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public Integer getTotalQuestions() {
        return totalQuestions;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public Integer getCorrectCount() {
        return correctCount;
    }

    public Integer getListeningCorrectCount() {
        return listeningCorrectCount;
    }

    public Integer getReadingCorrectCount() {
        return readingCorrectCount;
    }

    public String getSectionType() {
        return sectionType;
    }

    public Integer getConfiguredDurationSeconds() {
        return configuredDurationSeconds;
    }

    public boolean isSection() {
        return "SECTION_SNAPSHOT".equals(sourceType);
    }

    public boolean isFullTest() {
        return "TEST_VERSION".equals(sourceType);
    }

    public List<PracticeQuestionSnapshot> getQuestions() {
        return List.copyOf(questions);
    }

    // =====================================================
    // 5. GRADING
    // =====================================================

    public void completeGrading(
            OffsetDateTime submitted,
            int correct,
            int listening,
            int reading
    ) {
        if (!"IN_PROGRESS".equals(status)
                || submitted.isBefore(startedAt)
                || (!isSection()
                    && !submitted.isBefore(expiresAt))
                || correct < 0
                || correct > totalQuestions
                || listening < 0
                || reading < 0
                || listening + reading != correct) {

            throw new IllegalStateException(
                "Invalid practice grading transition"
            );
        }

        submittedAt = submitted;

        var elapsedUntil =
            isSection() && submitted.isAfter(expiresAt)
                ? expiresAt
                : submitted;

        durationSeconds = Math.toIntExact(
            java.time.temporal.ChronoUnit.SECONDS.between(
                startedAt,
                elapsedUntil
            )
        );

        correctCount = correct;
        listeningCorrectCount = listening;
        readingCorrectCount = reading;

        status = "GRADED";
    }
}
