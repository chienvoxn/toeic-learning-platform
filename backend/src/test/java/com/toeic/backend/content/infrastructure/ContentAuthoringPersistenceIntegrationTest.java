package com.toeic.backend.content.infrastructure;

import com.toeic.backend.content.application.ContentReadService;
import com.toeic.backend.content.domain.LearningContentVersion;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"debug=false", "logging.level.org.hibernate.orm.jdbc.error=OFF"})
@Sql(scripts = {"/fixtures/content-read.sql", "/fixtures/content-bookmark.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql(scripts = {"/fixtures/content03-domain-cleanup.sql", "/fixtures/content-bookmark-cleanup.sql", "/fixtures/content-read-cleanup.sql"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class ContentAuthoringPersistenceIntegrationTest {
    private static final UUID OWNER=UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER=UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID CONTENT=UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW=OffsetDateTime.parse("2026-11-04T16:00:00Z");
    @Autowired ContentRepository contents;
    @Autowired ContentVersionRepository versions;
    @Autowired ContentReadService published;
    @Autowired PlatformTransactionManager transactions;

    private LearningContentVersion save(LearningContentVersion version) {
        return new TransactionTemplate(transactions).execute(status -> versions.save(version));
    }

    @Test
    void existingOwnerCreatorAndTimestampsMapWithoutChangingThePublishedDto() {
        var content=contents.findReadableById(CONTENT,false,NOW).orElseThrow();
        assertEquals(OWNER,content.getAuthorId());
        assertNotNull(content.getCreatedAt());
        assertNotNull(content.getUpdatedAt());
        var version=versions.findForManagement(content.getCurrentPublishedVersion().getContentVersionId(),OWNER,false).orElseThrow();
        assertEquals(CONTENT,version.getContentId());
        assertEquals(OWNER,version.getCreatedBy());
        assertNotNull(version.getCreatedAt());
        assertNotNull(version.getUpdatedAt());
        assertEquals("Alpha published",published.detail(CONTENT).title());
        assertEquals(1,published.detail(CONTENT).media().size());
    }

    @Test
    void databaseOwnerScopeAppliesToVersionDataAndCount() {
        var page=PageRequest.of(0,1,Sort.by("versionNumber"));
        var own=versions.findVersionsForManagement(CONTENT,OWNER,false,page);
        assertEquals(3,own.getTotalElements());
        assertEquals(1,own.getContent().getFirst().getVersionNumber());
        var other=versions.findVersionsForManagement(CONTENT,OTHER,false,page);
        assertTrue(other.isEmpty());
        assertEquals(0,other.getTotalElements());
        var known=own.getContent().getFirst().getContentVersionId();
        assertTrue(versions.findForManagement(known,OTHER,false).isEmpty());
        // This is persistence capability, not a grant of the proposed Admin permission.
        assertEquals(3,versions.findVersionsForManagement(CONTENT,OTHER,true,page).getTotalElements());
        assertTrue(versions.findForManagement(known,OTHER,true).isPresent());
    }

    @Test
    void newDraftPreservesParentOwnerAndRecordsTheActualCreatorWithoutMovingPublishedPointer() {
        var version=save(new LearningContentVersion(CONTENT,4,OTHER," Draft title ",null,"",NOW));
        var loaded=versions.findForManagement(version.getContentVersionId(),OWNER,false).orElseThrow();
        assertEquals("DRAFT",loaded.getStatus());
        assertEquals("Draft title",loaded.getTitle());
        assertEquals("",loaded.getBody());
        assertNull(loaded.getPublishedAt());
        assertNull(loaded.getArchivedAt());
        assertEquals(OTHER,loaded.getCreatedBy());
        assertEquals(NOW.toInstant(),loaded.getCreatedAt().toInstant());
        assertEquals(NOW.toInstant(),loaded.getUpdatedAt().toInstant());
        var content=contents.findReadableById(CONTENT,false,NOW).orElseThrow();
        assertEquals(OWNER,content.getAuthorId());
        assertEquals(1,content.getCurrentPublishedVersion().getVersionNumber());
        assertEquals("Alpha published",published.detail(CONTENT).title());
    }

    @Test
    void draftEditPersistsTextWhileKeepingCreatorAndCreationTime() {
        var saved=save(new LearningContentVersion(CONTENT,4,OWNER,"Initial","Summary","Body",NOW));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var draft=versions.findForManagement(saved.getContentVersionId(),OWNER,false).orElseThrow();
            draft.replaceDraftContent(" Edited ",null,"New body",NOW.plusSeconds(10));
        });
        var edited=versions.findForManagement(saved.getContentVersionId(),OWNER,false).orElseThrow();
        assertEquals("Edited",edited.getTitle());
        assertNull(edited.getSummary());
        assertEquals("New body",edited.getBody());
        assertEquals(OWNER,edited.getCreatedBy());
        assertEquals(NOW.toInstant(),edited.getCreatedAt().toInstant());
        assertEquals(NOW.plusSeconds(10).toInstant(),edited.getUpdatedAt().toInstant());
        assertEquals("Alpha published",published.detail(CONTENT).title());
    }

    @ParameterizedTest
    @ValueSource(ints = {1,6})
    void publishedAndArchivedVersionsRejectDomainEdits(int number) {
        var id=UUID.fromString("30000000-0000-0000-0000-%012d".formatted(number));
        var original=versions.findForManagement(id,OWNER,false).orElseThrow();
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            versions.findForManagement(id,OWNER,false).orElseThrow().replaceDraftContent("Forbidden",null,"Forbidden",NOW);
        }));
        var unchanged=versions.findForManagement(id,OWNER,false).orElseThrow();
        assertEquals(original.getTitle(),unchanged.getTitle());
        assertEquals(original.getBody(),unchanged.getBody());
        assertEquals(original.getStatus(),unchanged.getStatus());
    }

    @Test
    void versionNumberUniqueAndParentCreatorForeignKeysAreEnforced() {
        assertThrows(DataIntegrityViolationException.class, () -> save(
                new LearningContentVersion(CONTENT,1,OWNER,"Duplicate",null,"",NOW)));
        assertThrows(DataIntegrityViolationException.class, () -> save(
                new LearningContentVersion(UUID.randomUUID(),1,OWNER,"Unknown parent",null,"",NOW)));
        assertThrows(DataIntegrityViolationException.class, () -> save(
                new LearningContentVersion(CONTENT,4,UUID.randomUUID(),"Unknown creator",null,"",NOW)));
        assertEquals(3,versions.findVersionsForManagement(CONTENT,OWNER,false,PageRequest.of(0,20)).getTotalElements());
    }
}
