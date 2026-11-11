package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.application.QuestionAuthoringSnapshotMapper;
import com.toeic.backend.questionbank.application.QuestionPromptMapper;
import com.toeic.backend.questionbank.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "debug=false"})
@Transactional
@Sql("/fixtures/question-domain.sql")
class QuestionAuthoringFoundationPersistenceTest {
    private static final UUID AUTHOR = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID AUDIO = UUID.fromString("62000000-0000-0000-0000-000000000001");
    @PersistenceContext EntityManager entityManager;
    @Autowired QuestionRepository questions;
    @Autowired QuestionVersionRepository versions;
    @Autowired QuestionAuthoringSnapshotMapper management;
    @Autowired QuestionPromptMapper read;
    @Autowired ObjectMapper json;

    private Question question() {
        var question = new Question(entityManager.createQuery("select p from ToeicPart p where p.partNumber=1", ToeicPart.class).getSingleResult(),
                entityManager.find(QuestionTopic.class, 910001), entityManager.find(QuestionType.class, 910001), null, AUTHOR);
        questions.persistNew(question);
        return question;
    }

    private QuestionVersion version(Question question, int number, String explanation) {
        var version = new QuestionVersion(question, number, null, "Prompt " + number, explanation, null);
        version.addChoices(List.of(new QuestionChoiceDraft("B", "B " + number, false, 2),
                new QuestionChoiceDraft("A", "A " + number, true, 1)));
        versions.persistNew(version);
        return version;
    }

    @Test
    void persistsBatchAndReadsExactVersionManagementAnswersWithoutChangingQ02Projection() throws Exception {
        var question = question();
        var first = version(question, 1, "SECRET_ONE");
        first.addResource(AUDIO);
        var second = version(question, 2, "SECRET_TWO");
        entityManager.flush();
        entityManager.clear();

        var storedFirst = versions.findById(first.getQuestionVersionId()).orElseThrow();
        var storedSecond = versions.findById(second.getQuestionVersionId()).orElseThrow();
        var firstDto = management.from(storedFirst);
        var secondDto = management.from(storedSecond);
        assertEquals("SECRET_ONE", firstDto.explanation());
        assertEquals("SECRET_TWO", secondDto.explanation());
        assertEquals(List.of("A", "B"), firstDto.choices().stream().map(c -> c.label()).toList());
        assertTrue(firstDto.choices().stream().allMatch(c -> c.questionVersionId().equals(first.getQuestionVersionId())));
        assertTrue(java.util.Collections.disjoint(firstDto.choices().stream().map(c -> c.choiceId()).toList(),
                secondDto.choices().stream().map(c -> c.choiceId()).toList()));
        assertTrue(firstDto.prompt().resourceIds().isEmpty());
        var readJson = json.writeValueAsString(read.toPrompt(storedFirst, java.util.Set.of(), java.util.Set.of()));
        assertFalse(readJson.contains("isCorrect"));
        assertFalse(readJson.contains("explanation"));
        assertFalse(readJson.contains("SECRET_ONE"));
        assertFalse(readJson.contains(AUDIO.toString()));
    }

    @Test
    void rejectedBatchLeavesPersistedDraftChoicesUnchangedAfterFlushAndReload() {
        var version = version(question(), 1, "ORIGINAL");
        entityManager.flush();
        var oldIds = version.getChoices().stream().map(element -> element.getChoiceId()).toList();
        assertThrows(IllegalArgumentException.class, () -> version.addChoices(List.of(
                new QuestionChoiceDraft("C", "New", false, 3), new QuestionChoiceDraft("A", "Duplicate", true, 4))));
        entityManager.flush();
        entityManager.clear();
        var stored = versions.findById(version.getQuestionVersionId()).orElseThrow();
        assertEquals(oldIds, stored.getChoices().stream().map(element -> element.getChoiceId()).toList());
        assertEquals("ORIGINAL", stored.getExplanation());
    }

    @Test
    void loadedPublishedVersionCannotReceiveChoiceBatch() {
        var version = version(question(), 1, "PUBLISHED_ORIGINAL");
        entityManager.flush();
        entityManager.createNativeQuery("update question_versions set status='PUBLISHED',published_at=now() where question_version_id=:id")
                .setParameter("id", version.getQuestionVersionId()).executeUpdate();
        entityManager.clear();
        var stored = versions.findById(version.getQuestionVersionId()).orElseThrow();
        assertThrows(IllegalStateException.class, () -> stored.addChoices(List.of(new QuestionChoiceDraft("C", "New", false, 3))));
        entityManager.flush();
        entityManager.clear();
        assertEquals(2, versions.findById(version.getQuestionVersionId()).orElseThrow().getChoices().size());
    }
}
