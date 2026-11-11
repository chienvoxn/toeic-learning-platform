package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.domain.*;
import com.toeic.backend.questionbank.domain.*;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"debug=false", "logging.level.org.hibernate.orm.jdbc.error=OFF"})
@Transactional
@Sql("/fixtures/question-domain.sql")
class PracticeAttemptRepositoryTest {
    private static final UUID ACTOR = UUID.fromString("51000000-0000-0000-0000-000000000001");
    @PersistenceContext EntityManager em;
    @Autowired PracticeAttemptRepository attempts;

    private QuestionVersion question(QuestionGroup group, QuestionGroupVersion gv) {
        var q = new Question(em.createQuery("select p from ToeicPart p where p.partNumber=1", ToeicPart.class).getSingleResult(), null, em.find(QuestionType.class, 910001), group, ACTOR);
        em.persist(q);
        var version = new QuestionVersion(q, 1, gv, "Fixture", "SECRET", null); em.persist(version);
        return version;
    }
    private PracticeAttempt attempt(List<PracticeAttempt.Pin> pins) {
        var now = OffsetDateTime.now(); return new PracticeAttempt(ACTOR, now, now.plusHours(1), pins);
    }
    @Test void repositoryPersistsExactVersionsOrderAndActorScopeOnPostgres() {
        var q1 = question(null, null); var q2 = question(null, null);
        var attempt = attempt(List.of(new PracticeAttempt.Pin(q2.getQuestionVersionId(), null), new PracticeAttempt.Pin(q1.getQuestionVersionId(), null)));
        attempts.persist(attempt); em.clear();
        var loaded = attempts.findOwn(ACTOR, attempt.getAttemptId()).orElseThrow();
        assertEquals(List.of(q2.getQuestionVersionId(), q1.getQuestionVersionId()), loaded.getQuestions().stream().map(PracticeQuestionSnapshot::getQuestionVersionId).toList());
        assertEquals(List.of(1, 2), loaded.getQuestions().stream().map(PracticeQuestionSnapshot::getQuestionOrder).toList());
        assertTrue(attempts.findOwn(UUID.randomUUID(), attempt.getAttemptId()).isEmpty());
        assertTrue(attempts.findOwn(ACTOR, UUID.randomUUID()).isEmpty());
        assertEquals(0L, em.createNativeQuery("select count(*) from attempt_answers where attempt_id=:id", Long.class).setParameter("id", attempt.getAttemptId()).getSingleResult());
    }
    @Test void compositeFkRejectsAGroupVersionFromAnotherQuestion() {
        var group = new QuestionGroup("PASSAGE"); em.persist(group);
        var first = new QuestionGroupVersion(group, 1, "One", "One"); em.persist(first);
        var second = new QuestionGroupVersion(group, 2, "Two", "Two"); em.persist(second);
        var q = question(group, first);
        var attempt = attempt(List.of(new PracticeAttempt.Pin(q.getQuestionVersionId(), second.getGroupVersionId())));
        assertThrows(DataIntegrityViolationException.class, () -> attempts.persist(attempt));
    }
    @Test void sourceTypeFilterDoesNotExposeTestVersionAttemptsThroughPracticeRepository() {
        var q = question(null, null);
        var attempt = attempt(List.of(new PracticeAttempt.Pin(q.getQuestionVersionId(), null)));
        attempts.persist(attempt);
        em.createNativeQuery("update attempts set attempt_source_type='TEST_VERSION' where attempt_id=:id").setParameter("id", attempt.getAttemptId()).executeUpdate();
        em.clear();
        assertTrue(attempts.findOwn(ACTOR, attempt.getAttemptId()).isEmpty());
    }
}
