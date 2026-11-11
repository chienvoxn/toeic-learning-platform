package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import com.toeic.backend.assessment.infrastructure.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Bảo vệ actor/ownership, tuần tự hóa ghi đáp án và nộp bài bằng khóa attempt. */
@Service
@PreAuthorize("hasAnyAuthority('practice:manage:self','exam:manage:self') and @practiceAccess.isActor(#actorId)")
public class PracticeSubmissionService {
    private final PracticeAttemptRepository attempts;
    private final PracticeAnswerRepository answers;
    private final Clock clock;
    private final AttemptGrading grading;
    private final PracticeAccess access;
    public PracticeSubmissionService(PracticeAttemptRepository attempts, PracticeAnswerRepository answers, Clock clock,
            AttemptGrading grading,PracticeAccess access) {
        this.attempts = attempts; this.answers = answers; this.clock = clock; this.grading = grading;this.access=access;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PracticeSessionDtos.SavedAnswer save(UUID actorId, UUID attemptId, UUID versionId, UUID choiceId) {
        var attempt = attempts.lockOwn(actorId, attemptId).orElseThrow(PracticeException::missing);
        access.requireSource(actorId,attempt.getSourceType());
        var now = now(); // Lấy giờ sau khi có khóa, kể cả request phải đợi qua thời hạn.
        requireEditable(attempt, now);
        if (attempt.getQuestions().stream().noneMatch(q -> q.getQuestionVersionId().equals(versionId))) {
            throw PracticeException.questionMissing();
        }
        if (choiceId != null && !answers.choiceBelongs(versionId, choiceId)) throw PracticeException.choiceMismatch();
        answers.save(attemptId, versionId, choiceId, now);
        return new PracticeSessionDtos.SavedAnswer(versionId, choiceId, now);
    }
    /** Khóa, chấm đúng pin và lưu toàn bộ kết quả cùng transaction; replay không gọi catalog. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PracticeResultDtos.Result submit(UUID actorId, UUID attemptId) {
        var attempt = attempts.lockOwn(actorId, attemptId).orElseThrow(PracticeException::missing);
        access.requireSource(actorId,attempt.getSourceType());
        if ("GRADED".equals(attempt.getStatus())) return grading.storedResult(attempt);
        var now = now();
        if(!"IN_PROGRESS".equals(attempt.getStatus()))throw PracticeException.notEditable();
        if(!attempt.isSection()&&!now.isBefore(attempt.getExpiresAt()))throw PracticeException.expired();
        return grading.grade(actorId,attempt,now);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PracticeResultDtos.Result result(UUID actorId, UUID attemptId) {
        var attempt = attempts.findOwn(actorId,attemptId).orElseThrow(PracticeException::missing);
        access.requireSource(actorId,attempt.getSourceType());
        if (!"GRADED".equals(attempt.getStatus())) throw PracticeException.resultNotReady();
        return grading.storedResult(attempt);
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS); }
    private static void requireEditable(PracticeAttempt attempt, OffsetDateTime now) {
        if (!"IN_PROGRESS".equals(attempt.getStatus())) throw PracticeException.notEditable();
        if (!now.isBefore(attempt.getExpiresAt())) throw PracticeException.expired();
    }
}
