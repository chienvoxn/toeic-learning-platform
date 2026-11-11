package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.domain.*;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"debug=false"})
@Transactional
@Sql("/fixtures/question-domain.sql")
class PracticePublicationRepositoryTest {
    private static final UUID ACTOR = UUID.fromString("51000000-0000-0000-0000-000000000001");
    @PersistenceContext EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PracticePublicationRepository candidates;
    private QuestionVersion question(QuestionGroup group, QuestionGroupVersion gv, int number, Question parent) {
        if (parent == null) {
            parent = new Question(em.createQuery("select p from ToeicPart p where p.partNumber=1", ToeicPart.class).getSingleResult(), null, em.find(QuestionType.class, 910001), group, ACTOR);
            em.persist(parent);
        }
        var version = new QuestionVersion(parent, number, gv, "Prompt", "HIDDEN", "EASY");
        em.persist(version); em.flush(); return version;
    }
    private void publish(QuestionVersion v) {
        jdbc.update("update question_versions set status='PUBLISHED',published_at=now()-interval '1 minute' where question_version_id=?", v.getQuestionVersionId());
        jdbc.update("update questions set status='PUBLISHED',current_published_version_id=? where question_id=?", v.getQuestionVersionId(), v.getQuestion().getQuestionId());
    }
    @Test void currentPointerOnlyNeverLatestOrFallback() {
        var first = question(null, null, 1, null); publish(first);
        var newer = question(null, null, 2, first.getQuestion());
        var rows = candidates.findPublished(Set.of(em.createQuery("select p.partId from ToeicPart p where p.partNumber=1", Integer.class).getSingleResult()), OffsetDateTime.now());
        assertEquals(List.of(first.getQuestionVersionId()), rows.stream().map(element -> element.questionVersionId()).toList());
        jdbc.update("update questions set current_published_version_id=null where question_id=?", first.getQuestion().getQuestionId());
        assertTrue(candidates.findPublished(Set.of(em.createQuery("select p.partId from ToeicPart p where p.partNumber=1", Integer.class).getSingleResult()), OffsetDateTime.now()).isEmpty());
        assertNotEquals(first.getQuestionVersionId(), newer.getQuestionVersionId());
    }
    @ParameterizedTest @ValueSource(strings={"draft", "archivedParent", "archivedVersion", "future", "archivedTimestamp"})
    void hiddenQuestionsDoNotEnterCandidatePool(String state) {
        var v = question(null, null, 1, null); publish(v);
        switch (state) {
            case "draft" -> jdbc.update("update question_versions set status='DRAFT' where question_version_id=?", v.getQuestionVersionId());
            case "archivedParent" -> jdbc.update("update questions set status='ARCHIVED' where question_id=?", v.getQuestion().getQuestionId());
            case "archivedVersion" -> jdbc.update("update question_versions set status='ARCHIVED' where question_version_id=?", v.getQuestionVersionId());
            case "future" -> jdbc.update("update question_versions set published_at=now()+interval '1 day' where question_version_id=?", v.getQuestionVersionId());
            case "archivedTimestamp" -> jdbc.update("update question_versions set archived_at=now() where question_version_id=?", v.getQuestionVersionId());
        }
        assertTrue(candidates.findPublished(Set.of(em.createQuery("select p.partId from ToeicPart p where p.partNumber=1", Integer.class).getSingleResult()), OffsetDateTime.now()).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"draftGroup", "archivedGroup", "draftGroupVersion", "archivedGroupVersion", "differentGroupPointer", "futureGroup"})
    void hiddenOrNonCurrentGroupNeverEntersCandidatePool(String state) {
        var g = new QuestionGroup("PASSAGE"); em.persist(g);
        var gv = new QuestionGroupVersion(g, 1, "Instruction", "Passage"); em.persist(gv);
        var other = new QuestionGroupVersion(g, 2, "Other", "Other"); em.persist(other);
        var v = question(g, gv, 1, null); publish(v);
        jdbc.update("update question_group_versions set status='PUBLISHED',published_at=now()-interval '1 minute' where group_version_id=?", gv.getGroupVersionId());
        jdbc.update("update question_groups set status='PUBLISHED',current_published_version_id=? where group_id=?", gv.getGroupVersionId(), g.getGroupId());
        assertEquals(1, candidates.findPublished(Set.of(em.createQuery("select p.partId from ToeicPart p where p.partNumber=1", Integer.class).getSingleResult()), OffsetDateTime.now()).size());
        switch (state) {
            case "draftGroup" -> jdbc.update("update question_groups set status='DRAFT' where group_id=?", g.getGroupId());
            case "archivedGroup" -> jdbc.update("update question_groups set status='ARCHIVED' where group_id=?", g.getGroupId());
            case "draftGroupVersion" -> jdbc.update("update question_group_versions set status='DRAFT' where group_version_id=?", gv.getGroupVersionId());
            case "archivedGroupVersion" -> jdbc.update("update question_group_versions set status='ARCHIVED' where group_version_id=?", gv.getGroupVersionId());
            case "differentGroupPointer" -> jdbc.update("update question_groups set current_published_version_id=? where group_id=?", other.getGroupVersionId(), g.getGroupId());
            case "futureGroup" -> jdbc.update("update question_group_versions set published_at=now()+interval '1 day' where group_version_id=?", gv.getGroupVersionId());
        }
        assertTrue(candidates.findPublished(Set.of(em.createQuery("select p.partId from ToeicPart p where p.partNumber=1", Integer.class).getSingleResult()), OffsetDateTime.now()).isEmpty());
    }
}
