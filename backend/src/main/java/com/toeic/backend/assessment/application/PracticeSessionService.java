package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import com.toeic.backend.assessment.infrastructure.PracticeAttemptRepository;
import com.toeic.backend.assessment.infrastructure.PracticeAnswerRepository;
import com.toeic.backend.questionbank.application.PracticeQuestionCatalog;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("hasAnyAuthority('practice:manage:self','exam:manage:self') and @practiceAccess.isActor(#actorId)")
public class PracticeSessionService {
    private final PracticeAttemptRepository attempts;
    private final PracticeQuestionCatalog catalog;
    private final PracticeUnitSelector selector;
    private final Clock clock;
    private final PracticeAnswerRepository answers;
    private final PracticeAccess access;
    public PracticeSessionService(PracticeAttemptRepository attempts,PracticeQuestionCatalog catalog,PracticeUnitSelector selector,Clock clock,
            PracticeAnswerRepository answers,PracticeAccess access) {
        this.attempts=attempts;this.catalog=catalog;this.selector=selector;this.clock=clock;this.answers=answers;this.access=access;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('practice:manage:self') and @practiceAccess.isActor(#actorId)")
    public PracticeSessionDtos.Session create(UUID actorId,PracticeStartQuery query) {
        if(query==null)throw PracticeException.invalid();
        var criteria=new PracticeQuestionCatalog.Criteria(new HashSet<>(query.partNumbers()),query.topicId(),query.difficulty());
        var now=OffsetDateTime.now(clock);
        var selected=selector.select(catalog.available(actorId,criteria,now),query);
        catalog.pin(actorId,criteria,selected,OffsetDateTime.now(clock));
        var started=OffsetDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var attempt=new PracticeAttempt(actorId,started,started.plusHours(24),selected.stream()
                .map(p->new PracticeAttempt.Pin(p.questionVersionId(),p.groupVersionId())).toList());
        attempts.persist(attempt);
        return view(actorId,attempt);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PracticeSessionDtos.Session get(UUID actorId,UUID attemptId) {
        return view(actorId,attempts.findOwn(actorId,attemptId).orElseThrow(PracticeException::missing));
    }
    PracticeSessionDtos.Session view(UUID actorId,PracticeAttempt attempt) {
        if(!attempt.getUserId().equals(actorId))throw PracticeException.missing();
        access.requireSource(actorId,attempt.getSourceType());
        var questions=new ArrayList<PracticeSessionDtos.Question>();
        for(var pin:attempt.getQuestions()) {
            var question=catalog.read(actorId,pin.getQuestionVersionId(),pin.getGroupVersionId());
            questions.add(PracticeSessionDtos.Question.from(pin.getQuestionOrder(),question));
        }
        if(questions.isEmpty()||questions.size()!=attempt.getTotalQuestions())throw new PracticeQuestionCatalog.Unavailable(false);
        var saved=answers.find(attempt.getAttemptId());
        var selections=attempt.getQuestions().stream().filter(q->saved.containsKey(q.getQuestionVersionId()))
                .map(q->{var a=saved.get(q.getQuestionVersionId());return new PracticeSessionDtos.SavedAnswer(a.questionVersionId(),a.selectedChoiceId(),a.updatedAt());}).toList();
        return new PracticeSessionDtos.Session(attempt.getAttemptId(),attempt.getSourceType(),attempt.getStatus(),attempt.getStartedAt(),
                attempt.getExpiresAt(),!OffsetDateTime.now(clock).isBefore(attempt.getExpiresAt()),attempt.getTotalQuestions(),questions,selections,
                SectionDtos.configuration(attempt));
    }
}
