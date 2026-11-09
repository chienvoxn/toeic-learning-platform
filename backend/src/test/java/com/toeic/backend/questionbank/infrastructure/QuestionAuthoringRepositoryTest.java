package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "debug=false"})
@Transactional
@Sql("/fixtures/question-domain.sql")
class QuestionAuthoringRepositoryTest {
    private static final UUID AUTHOR = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("51000000-0000-0000-0000-000000000002");
    @PersistenceContext EntityManager entityManager;
    @Autowired QuestionRepository questions;
    @Autowired QuestionVersionRepository versions;
    @Autowired QuestionGroupRepository groups;

    private Question question(UUID author) {
        var question = new Question(entityManager.find(ToeicPart.class, 910001),
                entityManager.find(QuestionTopic.class, 910001), entityManager.find(QuestionType.class, 910001), null, author);
        ReflectionTestUtils.setField(question, "updatedAt", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        questions.persistNew(question);
        return question;
    }

    private void otherActor() {
        entityManager.createNativeQuery("""
                insert into users (user_id,email,password_hash,full_name,status,created_at,updated_at)
                values (:id,'q03-other-fixture@example.invalid','unused-fixture-value','Other fixture','PENDING',now(),now())
                """).setParameter("id", OTHER).executeUpdate();
    }

    @Test
    void creatorScopeAppliesInDatabaseToEveryPageAndTotalIncludingEmptyPage() {
        otherActor();
        var first = question(AUTHOR);
        var second = question(AUTHOR);
        var foreign = question(OTHER);
        entityManager.flush();
        entityManager.clear();
        var expected = List.of(first.getQuestionId(), second.getQuestionId()).stream()
                .sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        var page0 = questions.findByCreator(AUTHOR, PageRequest.of(0, 1));
        var page1 = questions.findByCreator(AUTHOR, PageRequest.of(1, 1));
        var page2 = questions.findByCreator(AUTHOR, PageRequest.of(2, 1));
        assertEquals(expected.get(0), page0.getContent().getFirst().getQuestionId());
        assertEquals(expected.get(1), page1.getContent().getFirst().getQuestionId());
        assertEquals(2, page0.getTotalElements());
        assertEquals(2, page1.getTotalElements());
        assertEquals(2, page2.getTotalElements());
        assertTrue(page2.isEmpty());
        assertTrue(questions.findByCreator(OTHER, PageRequest.of(0, 100)).getContent().stream()
                .allMatch(question -> question.getQuestionId().equals(foreign.getQuestionId())));
        assertEquals(0, questions.findByCreator(UUID.randomUUID(), PageRequest.of(0, 20)).getTotalElements());
    }

    @Test
    void creatorScopedDetailCannotReadAnotherCreatorAndNullNeverBroadensScope() {
        var question = question(AUTHOR);
        entityManager.flush();
        entityManager.clear();
        assertTrue(questions.findByIdAndCreator(question.getQuestionId(), AUTHOR).isPresent());
        assertTrue(questions.findByIdAndCreator(question.getQuestionId(), OTHER).isEmpty());
        assertTrue(questions.findByIdAndCreator(UUID.randomUUID(), AUTHOR).isEmpty());
        assertThrows(NullPointerException.class, () -> questions.findByIdAndCreator(question.getQuestionId(), null));
        assertThrows(NullPointerException.class, () -> questions.findByCreator(null, PageRequest.of(0, 20)));
    }

    @Test
    void repositoryAllowsOnlyKnownSortFieldsAndBoundedPagination() {
        var first = question(AUTHOR);
        var second = question(AUTHOR);
        entityManager.flush();
        entityManager.clear();
        var expected = List.of(first.getQuestionId(), second.getQuestionId()).stream()
                .sorted(java.util.Comparator.comparing(UUID::toString).reversed()).toList();
        var actual = questions.findByCreator(AUTHOR, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "questionId")));
        assertEquals(expected, actual.getContent().stream().map(Question::getQuestionId).toList());
        // @Repository translates argument errors at its proxy boundary; retain the exact cause check.
        var invalidSort = assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                () -> questions.findByCreator(AUTHOR, PageRequest.of(0, 20, Sort.by("questionText"))));
        assertInstanceOf(IllegalArgumentException.class, invalidSort.getCause());
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                () -> questions.findByCreator(AUTHOR, PageRequest.of(0, 101)));
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                () -> questions.findByCreator(AUTHOR, null));
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                () -> questions.findByCreator(AUTHOR, org.springframework.data.domain.Pageable.unpaged()));
    }

    @Test
    void versionHistoriesStayWithinExactParentAndNeverSelectLatestImplicitly() {
        var first = question(AUTHOR);
        var other = question(AUTHOR);
        var first2 = new QuestionVersion(first, 2, null, "v2", "Answer2", null);
        var first1 = new QuestionVersion(first, 1, null, "v1", "Answer1", null);
        versions.persistNew(first2);
        versions.persistNew(first1);
        versions.persistNew(new QuestionVersion(other, 1, null, "Other", "OtherAnswer", null));
        var group = new QuestionGroup("PASSAGE");
        var otherGroup = new QuestionGroup("PASSAGE");
        groups.persistNew(group);
        groups.persistNew(otherGroup);
        groups.persistNew(new QuestionGroupVersion(group, 2, "v2", "Passage2"));
        groups.persistNew(new QuestionGroupVersion(group, 1, "v1", "Passage1"));
        groups.persistNew(new QuestionGroupVersion(otherGroup, 1, "Other", "OtherPassage"));
        entityManager.flush();
        entityManager.clear();
        assertEquals(List.of(1, 2), versions.findAllForQuestion(first.getQuestionId()).stream()
                .map(QuestionVersion::getVersionNumber).toList());
        assertTrue(versions.findAllForQuestion(first.getQuestionId()).stream()
                .allMatch(version -> version.getQuestion().getQuestionId().equals(first.getQuestionId())));
        assertTrue(versions.findAllForQuestion(UUID.randomUUID()).isEmpty());
        assertEquals(List.of(1, 2), groups.findAllVersions(group.getGroupId()).stream()
                .map(QuestionGroupVersion::getVersionNumber).toList());
        assertTrue(groups.findAllVersions(group.getGroupId()).stream()
                .allMatch(version -> version.getGroup().getGroupId().equals(group.getGroupId())));
        assertTrue(groups.findAllVersions(UUID.randomUUID()).isEmpty());
    }
}
