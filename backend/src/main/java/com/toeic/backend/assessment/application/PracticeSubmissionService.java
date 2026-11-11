package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import com.toeic.backend.assessment.infrastructure.*;
import com.toeic.backend.questionbank.application.PracticeQuestionCatalog;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Bảo vệ actor/ownership, tuần tự hóa ghi đáp án và nộp bài bằng khóa attempt. */
@Service
@PreAuthorize("hasAuthority('practice:manage:self') and @practiceAccess.isActor(#actorId)")
public class PracticeSubmissionService {
    private final PracticeAttemptRepository attempts;
    private final PracticeAnswerRepository answers;
    private final Clock clock;
    private final PracticeQuestionCatalog catalog;
    public PracticeSubmissionService(PracticeAttemptRepository attempts, PracticeAnswerRepository answers, Clock clock,
            PracticeQuestionCatalog catalog) {
        this.attempts = attempts; this.answers = answers; this.clock = clock; this.catalog = catalog;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PracticeSessionDtos.SavedAnswer save(UUID actorId, UUID attemptId, UUID versionId, UUID choiceId) {
        var attempt = attempts.lockOwn(actorId, attemptId).orElseThrow(PracticeException::missing);
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
        if ("GRADED".equals(attempt.getStatus())) return storedResult(attempt);
        var now = now();
        requireEditable(attempt, now);
        var saved = answers.find(attemptId);
        var pins = attempt.getQuestions();
        if (pins.size() != attempt.getTotalQuestions() || pins.isEmpty()
                || !pins.stream().map(q -> q.getQuestionVersionId()).collect(java.util.stream.Collectors.toSet()).containsAll(saved.keySet())) {
            throw new PracticeQuestionCatalog.Unavailable(false);
        }
        int correct = 0, listening = 0, reading = 0;
        var parts = new TreeMap<Integer,int[]>();
        for (var pin : pins) {
            var versionId = pin.getQuestionVersionId();
            var key = catalog.markingKey(actorId,versionId,pin.getGroupVersionId());
            var selected = saved.containsKey(versionId) ? saved.get(versionId).selectedChoiceId() : null;
            if (selected != null && !answers.choiceBelongs(versionId,selected)) throw new PracticeQuestionCatalog.Unavailable(false);
            boolean right = key.correctChoiceId().equals(selected);
            answers.mark(attemptId,versionId,selected,right,key.correctChoiceId(),key.explanation(),key.partNumber(),now);
            var part = parts.computeIfAbsent(key.partId(),id -> new int[2]);
            part[0]++;
            if (right) {
                part[1]++;correct++;
                if (key.partNumber() <= 4) listening++; else reading++;
            }
        }
        for (var part : parts.entrySet()) {
            var count = part.getValue();
            answers.partResult(attemptId,part.getKey(),count[0],count[1],accuracy(count[1],count[0]));
        }
        attempt.completeGrading(now,correct,listening,reading);
        return storedResult(attempt);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PracticeResultDtos.Result result(UUID actorId, UUID attemptId) {
        var attempt = attempts.findOwn(actorId,attemptId).orElseThrow(PracticeException::missing);
        if (!"GRADED".equals(attempt.getStatus())) throw PracticeException.resultNotReady();
        return storedResult(attempt);
    }
    private PracticeResultDtos.Result storedResult(PracticeAttempt attempt) {
        var saved = answers.find(attempt.getAttemptId());
        var questions = new ArrayList<PracticeResultDtos.QuestionResult>();
        int correct = 0, incorrect = 0, unanswered = 0;
        for (var pin : attempt.getQuestions()) {
            var answer = saved.get(pin.getQuestionVersionId());
            if (answer == null || answer.correct() == null || answer.correctChoiceId() == null || answer.partNumber() == null) {
                throw new PracticeQuestionCatalog.Unavailable(false);
            }
            String outcome;
            if (answer.selectedChoiceId() == null) { unanswered++; outcome = "UNANSWERED"; }
            else if (answer.correct()) { correct++; outcome = "CORRECT"; }
            else { incorrect++; outcome = "INCORRECT"; }
            questions.add(new PracticeResultDtos.QuestionResult(pin.getQuestionOrder(),pin.getQuestionVersionId(),answer.partNumber(),
                    answer.selectedChoiceId(),answer.correctChoiceId(),answer.correct(),outcome,answer.explanation()));
        }
        if (questions.size() != attempt.getTotalQuestions() || saved.size() != questions.size()
                || !Objects.equals(attempt.getCorrectCount(),correct) || attempt.getSubmittedAt() == null) {
            throw new PracticeQuestionCatalog.Unavailable(false);
        }
        return new PracticeResultDtos.Result(attempt.getAttemptId(),attempt.getStatus(),attempt.getSubmittedAt(),attempt.getTotalQuestions(),
                correct,incorrect,unanswered,correct,accuracy(correct,attempt.getTotalQuestions()),attempt.getListeningCorrectCount(),
                attempt.getReadingCorrectCount(),questions);
    }
    private static BigDecimal accuracy(int correct, int total) {
        return BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS); }
    private static void requireEditable(PracticeAttempt attempt, OffsetDateTime now) {
        if (!"IN_PROGRESS".equals(attempt.getStatus())) throw PracticeException.notEditable();
        if (!now.isBefore(attempt.getExpiresAt())) throw PracticeException.expired();
    }
}
