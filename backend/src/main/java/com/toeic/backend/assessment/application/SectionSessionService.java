package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.PracticeAttempt;
import com.toeic.backend.assessment.infrastructure.PracticeAttemptRepository;
import com.toeic.backend.questionbank.application.PracticeQuestionCatalog;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Tạo Section đủ mọi Part, dùng lại selector/catalog/khóa nguồn và safe session của Practice. */
@Service
@PreAuthorize("hasAuthority('exam:manage:self') and @practiceAccess.isActor(#actorId)")
public class SectionSessionService {
    private final PracticeAttemptRepository attempts;
    private final PracticeQuestionCatalog catalog;
    private final PracticeUnitSelector selector;
    private final PracticeSessionService sessions;
    private final Clock clock;
    public SectionSessionService(PracticeAttemptRepository attempts,PracticeQuestionCatalog catalog,PracticeUnitSelector selector,
            PracticeSessionService sessions,Clock clock) {
        this.attempts=attempts;this.catalog=catalog;this.selector=selector;this.sessions=sessions;this.clock=clock;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PracticeSessionDtos.Session create(UUID actorId,SectionStartQuery query) {
        if(query==null)throw PracticeException.invalid();
        if(query.questionCount()<query.partNumbers().size())throw PracticeException.sectionInsufficient();
        var criteria=new PracticeQuestionCatalog.Criteria(new HashSet<>(query.partNumbers()),null,null);
        List<PracticeQuestionCatalog.Pin> selected;
        try { selected=selector.select(catalog.available(actorId,criteria,OffsetDateTime.now(clock)),query.selection()); }
        catch(PracticeException ex) {
            if("PRACTICE_QUESTIONS_INSUFFICIENT".equals(ex.getCode()))throw PracticeException.sectionInsufficient();
            throw ex;
        }
        catalog.pin(actorId,criteria,selected,OffsetDateTime.now(clock));
        var started=OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        var attempt=PracticeAttempt.section(actorId,started,query.type(),query.durationMinutes(),selected.stream()
                .map(p->new PracticeAttempt.Pin(p.questionVersionId(),p.groupVersionId())).toList());
        attempts.persist(attempt);
        return sessions.view(actorId,attempt);
    }
}
