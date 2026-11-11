package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import com.toeic.backend.assessment.infrastructure.PracticeAnswerRepository;
import com.toeic.backend.questionbank.application.PracticeQuestionCatalog;
import java.math.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Chấm/lưu snapshot và đọc kết quả dùng chung; use case gọi sau khi kiểm quyền, ownership và giữ khóa attempt. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class AttemptGrading {
    private final PracticeAnswerRepository answers;
    private final PracticeQuestionCatalog catalog;
    AttemptGrading(PracticeAnswerRepository answers, PracticeQuestionCatalog catalog) {
        this.answers = answers; this.catalog = catalog;
    }
    PracticeResultDtos.Result grade(UUID actorId, PracticeAttempt attempt, OffsetDateTime now) {
        var attemptId = attempt.getAttemptId();
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
            if(attempt.isSection()&&!SectionDtos.configuration(attempt).partNumbers().contains(key.partNumber())) {
                throw new PracticeQuestionCatalog.Unavailable(false);
            }
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
    PracticeResultDtos.Result storedResult(PracticeAttempt attempt) {
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
                attempt.getReadingCorrectCount(),questions,SectionDtos.configuration(attempt),
                attempt.isSection()?partResults(attempt,questions):null);
    }
    private List<SectionDtos.PartResult> partResults(PracticeAttempt attempt,List<PracticeResultDtos.QuestionResult> questions) {
        var persisted=answers.partResults(attempt.getAttemptId());
        var required=SectionDtos.configuration(attempt).partNumbers();
        if(!persisted.stream().map(p->p.partNumber()).toList().equals(required))throw new PracticeQuestionCatalog.Unavailable(false);
        return persisted.stream().map(p->{
            var rows=questions.stream().filter(q->q.partNumber()==p.partNumber()).toList();
            int unanswered=(int)rows.stream().filter(q->q.selectedChoiceId()==null).count();
            if(rows.size()!=p.totalQuestions()||rows.stream().filter(q->q.isCorrect()).count()!=p.correctCount()) {
                throw new PracticeQuestionCatalog.Unavailable(false);
            }
            return new SectionDtos.PartResult(p.partNumber(),p.totalQuestions(),p.correctCount(),p.totalQuestions()-p.correctCount()-unanswered,
                    unanswered,p.correctCount(),p.accuracy());
        }).toList();
    }
    private static BigDecimal accuracy(int correct, int total) {
        return BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);
    }
}
