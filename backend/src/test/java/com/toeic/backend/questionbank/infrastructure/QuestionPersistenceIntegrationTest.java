package com.toeic.backend.questionbank.infrastructure;

import com.toeic.backend.questionbank.application.QuestionPromptMapper;
import com.toeic.backend.questionbank.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "debug=false"})
@Transactional
@Sql("/fixtures/question-domain.sql")
class QuestionPersistenceIntegrationTest {
    private static final UUID AUTHOR = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID AUDIO = UUID.fromString("62000000-0000-0000-0000-000000000001");
    private static final UUID IMAGE = UUID.fromString("62000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("62000000-0000-0000-0000-000000000003");
    @PersistenceContext EntityManager entityManager;
    @Autowired QuestionRepository questions;
    @Autowired QuestionVersionRepository versions;
    @Autowired QuestionGroupRepository groups;
    @Autowired QuestionAssetRepository assets;
    @Autowired QuestionPromptMapper prompts;
    @Autowired ObjectMapper mapper;
    private ToeicPart part;
    private QuestionTopic topic;
    private QuestionType type;

    @BeforeEach
    void references() {
        part = entityManager.find(ToeicPart.class, 910001);
        topic = entityManager.find(QuestionTopic.class, 910001);
        type = entityManager.find(QuestionType.class, 910001);
    }
    private Question newQuestion(QuestionGroup group) {
        var question = new Question(part, topic, type, group, AUTHOR);
        questions.persistNew(question);
        return question;
    }
    private QuestionVersion version(Question question, int number, QuestionGroupVersion group) {
        var version = new QuestionVersion(question, number, group, "Prompt v" + number, "ANSWER_SECRET_" + number, "EASY");
        version.addChoice("B", "Choice B v" + number, false, 2);
        version.addChoice("A", "Choice A v" + number, true, 1);
        return version;
    }
    private QuestionVersion storedVersion() {
        var version = version(newQuestion(null), 1, null);
        versions.persistNew(version);
        entityManager.flush();
        return version;
    }
    private void assertSqlState(String expected, Runnable operation) {
        var failure = assertThrows(PersistenceException.class, operation::run);
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) { cause = cause.getCause(); }
        var sqlException = assertInstanceOf(SQLException.class, cause);
        assertEquals(expected, sqlException.getSQLState());
    }

    @Test
    void persistsAndReadsPinnedVersionsChoicesAnswersPassagesAndAssetsWithoutMixing() throws Exception {
        var group = new QuestionGroup("PASSAGE");
        groups.persistNew(group);
        var passage1 = new QuestionGroupVersion(group, 1, "Instruction 1", "Passage 1");
        passage1.addResource(AUDIO, 2);
        passage1.addResource(OTHER, 1);
        passage1.addResource(IMAGE, 1);
        groups.persistNew(passage1);
        var passage2 = new QuestionGroupVersion(group, 2, "Instruction 2", "Passage 2");
        passage2.addResource(OTHER, 1);
        groups.persistNew(passage2);
        var question = newQuestion(group);
        var first = version(question, 1, passage1);
        first.addResource(IMAGE);
        first.addResource(AUDIO);
        versions.persistNew(first);
        var second = version(question, 2, passage2);
        second.addResource(OTHER);
        var c = second.addChoice("C", "Choice C v2", true, 3);
        versions.persistNew(second);
        var firstCorrect = first.answerKey().correctChoiceIds();
        entityManager.flush();
        entityManager.clear();

        var stored = versions.findByQuestionAndNumber(question.getQuestionId(), 1).orElseThrow();
        var stored2 = versions.findById(second.getQuestionVersionId()).orElseThrow();
        var metadata = questions.findById(question.getQuestionId()).orElseThrow();
        assertEquals(1, metadata.getPart().getPartNumber());
        assertEquals("LISTENING", metadata.getPart().getSkill());
        assertEquals("Q01 fixture topic", metadata.getTopic().getName());
        assertEquals("Q01_FIXTURE", metadata.getType().getCode());
        assertEquals("No choice count policy implied", metadata.getType().getDescription());
        assertEquals(AUTHOR, metadata.getCreatedBy());
        assertNotNull(metadata.getCreatedAt());
        assertEquals(List.of("A", "B"), stored.getChoices().stream().map(element -> element.getLabel()).toList());
        assertTrue(stored.getChoices().stream().allMatch(choice -> choice.getQuestionVersionId().equals(stored.getQuestionVersionId())));
        assertEquals(firstCorrect, stored.answerKey().correctChoiceIds());
        assertEquals("ANSWER_SECRET_1", stored.getExplanation());
        assertEquals("ANSWER_SECRET_2", stored2.answerKey().explanation());
        assertTrue(stored2.answerKey().correctChoiceIds().contains(c.getChoiceId()));
        assertTrue(java.util.Collections.disjoint(firstCorrect, stored2.answerKey().correctChoiceIds()));
        assertEquals(passage1.getGroupVersionId(), stored.getGroupVersion().getGroupVersionId());
        assertEquals("Passage 1", stored.getGroupVersion().getPassageText());
        assertEquals("Passage 2", stored2.getGroupVersion().getPassageText());
        assertEquals(List.of(IMAGE, OTHER, AUDIO), stored.getGroupVersion().getResources().stream()
                .map(element -> element.getResourceId()).toList());
        assertEquals(List.of(AUDIO, IMAGE), stored.getResources().stream().map(element -> element.getResourceId()).toList());
        assertEquals(List.of(OTHER), stored2.getResources().stream().map(element -> element.getResourceId()).toList());
        assertEquals(List.of(AUDIO, IMAGE), assets.findForQuestionVersion(stored.getQuestionVersionId()).stream()
                .map(element -> element.resourceId()).toList());
        assertEquals(List.of(IMAGE, OTHER, AUDIO), assets.findForGroupVersion(passage1.getGroupVersionId()).stream()
                .map(element -> element.resourceId()).toList());
        var media = assets.findForQuestionVersion(stored2.getQuestionVersionId()).getFirst();
        assertEquals(OTHER, media.resourceId());
        assertNull(media.fileSize());
        var audio = assets.findForGroupVersion(passage1.getGroupVersionId()).getLast();
        assertEquals(1200L, audio.fileSize());
        assertEquals(30, audio.durationSeconds());
        assertEquals("audio/mpeg", audio.mimeType());
        assertEquals("AUDIO", audio.resourceType());

        var prompt = prompts.toPrompt(stored);
        var json = mapper.writeValueAsString(prompt);
        assertEquals("Passage 1", prompt.passage().passageText());
        assertEquals(2, prompt.choices().size());
        assertFalse(json.contains("ANSWER_SECRET"));
        assertFalse(json.contains("isCorrect"));
        assertFalse(json.contains("explanation"));
        assertFalse(json.contains("correctChoiceIds"));
        assertFalse(json.contains("storageUrl"));
        assertFalse(json.contains("transcript"));
        assertFalse(json.contains("Prompt v2"));
        assertFalse(json.contains("Passage 2"));
        assertFalse(mapper.writeValueAsString(stored.getChoices().getFirst()).contains("correct"));
    }

    @Test
    void standaloneQuestionSupportsOptionalFieldsAndMissingLookups() {
        var question = new Question(part, null, type, null, AUTHOR);
        questions.persistNew(question);
        var draft = new QuestionVersion(question, 1, null, "Standalone", null, null);
        versions.persistNew(draft);
        entityManager.flush();
        entityManager.clear();
        var stored = versions.findById(draft.getQuestionVersionId()).orElseThrow();
        assertNull(stored.getQuestion().getTopic());
        assertNull(stored.getDifficulty());
        assertNull(stored.getExplanation());
        assertNull(prompts.toPrompt(stored).passage());
        assertTrue(stored.getChoices().isEmpty());
        assertTrue(questions.findById(UUID.randomUUID()).isEmpty());
        assertTrue(versions.findById(UUID.randomUUID()).isEmpty());
        assertTrue(versions.findByQuestionAndNumber(question.getQuestionId(), 99).isEmpty());
        assertTrue(groups.findById(UUID.randomUUID()).isEmpty());
        assertTrue(groups.findVersionById(UUID.randomUUID()).isEmpty());
        assertTrue(assets.findForQuestionVersion(UUID.randomUUID()).isEmpty());
        assertTrue(assets.findForGroupVersion(UUID.randomUUID()).isEmpty());
    }

    @Test
    void mappedLookupEntitiesCanBePersistedWithIdentityGeneratedIds() {
        var anotherPart = new ToeicPart(2, "LISTENING", "Second fixture part");
        var anotherTopic = new QuestionTopic("Another Q01 topic", "Description");
        var anotherType = new QuestionType("OTHER_FIXTURE", "Other type", null);
        entityManager.persist(anotherPart);
        entityManager.persist(anotherTopic);
        entityManager.persist(anotherType);
        entityManager.flush();
        entityManager.clear();
        assertNotNull(anotherPart.getPartId());
        assertEquals("Description", entityManager.find(QuestionTopic.class, anotherTopic.getTopicId()).getDescription());
        assertEquals("OTHER_FIXTURE", entityManager.find(QuestionType.class, anotherType.getQuestionTypeId()).getCode());
    }

    @Test
    void editingDraftWritesOnlyThatVersion() {
        var first = storedVersion();
        var second = version(first.getQuestion(), 2, null);
        versions.persistNew(second);
        first.reviseDraft("Changed draft", "New explanation", "CUSTOM");
        entityManager.flush();
        entityManager.clear();
        assertEquals("Changed draft", versions.findById(first.getQuestionVersionId()).orElseThrow().getQuestionText());
        assertEquals("CUSTOM", versions.findById(first.getQuestionVersionId()).orElseThrow().getDifficulty());
        assertEquals("Prompt v2", versions.findById(second.getQuestionVersionId()).orElseThrow().getQuestionText());
        assertEquals("ANSWER_SECRET_2", versions.findById(second.getQuestionVersionId()).orElseThrow().getExplanation());
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_REVIEW", "APPROVED", "PUBLISHED", "ARCHIVED"})
    void nonDraftVersionsRejectAllImplementedContentMutators(String status) {
        var version = storedVersion();
        entityManager.createNativeQuery("update question_versions set status = :status, reviewed_by = :actor, reviewed_at = now(), approved_by = :actor, approved_at = now(), published_at = now(), archived_at = now() where question_version_id = :id")
                .setParameter("status", status).setParameter("actor", AUTHOR).setParameter("id", version.getQuestionVersionId()).executeUpdate();
        entityManager.clear();
        var frozen = versions.findById(version.getQuestionVersionId()).orElseThrow();
        assertEquals(status, frozen.getStatus());
        assertEquals(AUTHOR, frozen.getReviewedBy());
        assertEquals(AUTHOR, frozen.getApprovedBy());
        assertNotNull(frozen.getReviewedAt());
        assertNotNull(frozen.getApprovedAt());
        assertNotNull(frozen.getPublishedAt());
        assertNotNull(frozen.getArchivedAt());
        assertThrows(IllegalStateException.class, () -> frozen.reviseDraft("Changed", "Changed", "HARD"));
        assertThrows(IllegalStateException.class, () -> frozen.addChoice("C", "C", false, 3));
        assertThrows(IllegalStateException.class, () -> frozen.addResource(AUDIO));
        assertEquals("Prompt v1", frozen.getQuestionText());
    }

    @Test
    void groupSnapshotCannotAcceptAssetsAfterPersistOrLoad() {
        var group = new QuestionGroup("PASSAGE");
        groups.persistNew(group);
        var version = new QuestionGroupVersion(group, 1, "Instruction", "Passage");
        groups.persistNew(version);
        entityManager.flush();
        assertThrows(IllegalStateException.class, () -> version.addResource(AUDIO, 1));
        entityManager.clear();
        var stored = groups.findVersionById(version.getGroupVersionId()).orElseThrow();
        assertEquals(group.getGroupId(), groups.findById(group.getGroupId()).orElseThrow().getGroupId());
        assertEquals("PASSAGE", stored.getGroup().getGroupType());
        assertNotNull(stored.getCreatedAt());
        assertThrows(IllegalStateException.class, () -> stored.addResource(AUDIO, 1));
    }

    @Test
    void existingSchemaAllowsGroupMismatchButPromptMapperRefusesToExposeIt() {
        var group1 = new QuestionGroup("PASSAGE");
        var group2 = new QuestionGroup("PASSAGE");
        groups.persistNew(group1);
        groups.persistNew(group2);
        var first = new QuestionGroupVersion(group1, 1, null, "First");
        var other = new QuestionGroupVersion(group2, 1, null, "Other");
        groups.persistNew(first);
        groups.persistNew(other);
        var version = version(newQuestion(group1), 1, first);
        versions.persistNew(version);
        entityManager.flush();
        entityManager.createNativeQuery("update question_versions set group_version_id = :other where question_version_id = :id")
                .setParameter("other", other.getGroupVersionId()).setParameter("id", version.getQuestionVersionId()).executeUpdate();
        entityManager.clear();
        var inconsistent = versions.findById(version.getQuestionVersionId()).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> prompts.toPrompt(inconsistent));
    }

    @Test
    void databaseRejectsDuplicateQuestionVersionNumber() {
        var version = storedVersion();
        var duplicate = version(version.getQuestion(), 1, null);
        assertSqlState("23505", () -> { versions.persistNew(duplicate); entityManager.flush(); });
    }

    @ParameterizedTest
    @ValueSource(strings = {"LABEL", "ORDER"})
    void databaseRejectsDuplicateChoiceLabelOrOrder(String duplicate) {
        var version = storedVersion();
        assertSqlState("23505", () -> entityManager.createNativeQuery("""
                insert into question_choices (choice_id, question_version_id, choice_label, content, is_correct, display_order)
                values (:choice, :version, :label, 'Fixture', false, :position)
                """).setParameter("choice", UUID.randomUUID()).setParameter("version", version.getQuestionVersionId())
                .setParameter("label", duplicate.equals("LABEL") ? "A" : "C")
                .setParameter("position", duplicate.equals("ORDER") ? 1 : 99).executeUpdate());
    }

    @Test
    void databaseRejectsChoiceWhoseVersionDoesNotExist() {
        assertSqlState("23503", () -> entityManager.createNativeQuery("""
                insert into question_choices (choice_id, question_version_id, choice_label, content, is_correct, display_order)
                values (:id, :version, 'A', 'Fixture', false, 1)
                """).setParameter("id", UUID.randomUUID()).setParameter("version", UUID.randomUUID()).executeUpdate());
    }

    @Test
    void databaseRejectsResourceWhoseMediaDoesNotExist() {
        var version = version(newQuestion(null), 1, null);
        version.addResource(UUID.randomUUID());
        assertSqlState("23503", () -> { versions.persistNew(version); entityManager.flush(); });
    }

    @Test
    void databaseRejectsDuplicateVersionResourceLink() {
        var version = version(newQuestion(null), 1, null);
        version.addResource(AUDIO);
        versions.persistNew(version);
        entityManager.flush();
        assertSqlState("23505", () -> entityManager.createNativeQuery("insert into question_resources values (:version, :resource)")
                .setParameter("version", version.getQuestionVersionId()).setParameter("resource", AUDIO).executeUpdate());
    }

    @Test
    void databaseRejectsMissingGroupVersionReference() {
        var version = storedVersion();
        assertSqlState("23503", () -> entityManager.createNativeQuery("update question_versions set group_version_id = :group where question_version_id = :id")
                .setParameter("group", UUID.randomUUID()).setParameter("id", version.getQuestionVersionId()).executeUpdate());
    }

    @Test
    void databaseRejectsDuplicateGroupVersionNumber() {
        var group = new QuestionGroup("PASSAGE");
        groups.persistNew(group);
        groups.persistNew(new QuestionGroupVersion(group, 1, null, "First"));
        entityManager.flush();
        assertSqlState("23505", () -> { groups.persistNew(new QuestionGroupVersion(group, 1, null, "Other")); entityManager.flush(); });
    }

    @Test
    void databaseRejectsMissingCreator() {
        var question = new Question(part, topic, type, null, UUID.randomUUID());
        assertSqlState("23503", () -> { questions.persistNew(question); entityManager.flush(); });
    }
}
