package com.toeic.backend.content.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"debug=false", "logging.level.org.hibernate.orm.jdbc.error=OFF"})
@Sql(scripts = {"/fixtures/content-read.sql", "/fixtures/content-bookmark.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql(scripts = {"/fixtures/content-bookmark-cleanup.sql", "/fixtures/content-read-cleanup.sql"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class ContentBookmarkRepositoryIntegrationTest {
    private static final UUID ACTOR = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-04T00:00:00Z");
    private static final Sort ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id.contentId"));
    @Autowired ContentBookmarkRepository bookmarks;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private static UUID content(int number) {
        return UUID.fromString("20000000-0000-0000-0000-%012d".formatted(number));
    }

    private int insert(UUID user, int number) {
        return new TransactionTemplate(transactions).execute(status -> bookmarks.insertIfAbsent(user, content(number), NOW));
    }

    @Test
    void duplicateInsertKeepsOriginalTimeAndSeparateUsersHaveSeparateRows() {
        assertEquals(1, insert(ACTOR, 1));
        int duplicate = new TransactionTemplate(transactions).execute(status ->
                bookmarks.insertIfAbsent(ACTOR, content(1), NOW.plusDays(1)));
        assertEquals(0, duplicate);
        assertEquals(1, insert(OTHER, 1));
        var result = bookmarks.findReadable(ACTOR, true, NOW, PageRequest.of(0, 20, ORDER));
        assertEquals(1, result.getTotalElements());
        assertEquals(NOW.toInstant(), result.getContent().getFirst().getCreatedAt().toInstant());
        assertEquals(content(1), result.getContent().getFirst().getContent().getContentId());
        assertEquals("Alpha published", result.getContent().getFirst().getContent().getCurrentPublishedVersion().getTitle());
        assertEquals(1, bookmarks.findReadable(OTHER, true, NOW, PageRequest.of(0, 20, ORDER)).getTotalElements());
    }

    @Test
    void listAndCountApplyActorAndCurrentVisibilityWithStablePages() {
        for (int number = 1; number <= 11; number++) { insert(ACTOR, number); }
        insert(OTHER, 1);
        var first = bookmarks.findReadable(ACTOR, false, NOW, PageRequest.of(0, 1, ORDER));
        var second = bookmarks.findReadable(ACTOR, false, NOW, PageRequest.of(1, 1, ORDER));
        assertEquals(2, first.getTotalElements());
        assertEquals(2, second.getTotalElements());
        assertEquals(content(1), first.getContent().getFirst().getContent().getContentId());
        assertEquals(content(2), second.getContent().getFirst().getContent().getContentId());
        assertEquals(3, bookmarks.findReadable(ACTOR, true, NOW, PageRequest.of(0, 1, ORDER)).getTotalElements());
        assertEquals(1, bookmarks.findReadable(OTHER, true, NOW, PageRequest.of(0, 1, ORDER)).getTotalElements());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 7, 8, 9, 10, 11})
    void hiddenBookmarkedContentDoesNotAppearInDataOrCount(int number) {
        insert(ACTOR, number);
        for (var member : List.of(false, true)) {
            var page = bookmarks.findReadable(ACTOR, member, NOW, PageRequest.of(0, 1, ORDER));
            assertTrue(page.isEmpty());
            assertEquals(0, page.getTotalElements());
        }
    }

    @Test
    void permissionScopeAndPublicationChangesApplyToExistingBookmarkRows() {
        insert(ACTOR, 3);
        assertEquals(1, bookmarks.findReadable(ACTOR, true, NOW, PageRequest.of(0, 1, ORDER)).getTotalElements());
        assertEquals(0, bookmarks.findReadable(ACTOR, false, NOW, PageRequest.of(0, 1, ORDER)).getTotalElements());
        jdbc.update("update learning_contents set visibility='PRIVATE' where content_id=?", content(3));
        assertEquals(0, bookmarks.findReadable(ACTOR, true, NOW, PageRequest.of(0, 1, ORDER)).getTotalElements());
    }

    @Test
    void deleteIsScopedToBothUserAndContentEvenWhenTheContentIsHidden() {
        insert(ACTOR, 4);
        insert(OTHER, 4);
        var transaction = new TransactionTemplate(transactions);
        int deleted = transaction.execute(status -> bookmarks.deleteOwned(ACTOR, content(4)));
        int repeated = transaction.execute(status -> bookmarks.deleteOwned(ACTOR, content(4)));
        assertEquals(1, deleted);
        assertEquals(0, repeated);
        assertEquals(1, jdbc.queryForObject("select count(*) from bookmarks where user_id=? and content_id=?",
                Integer.class, OTHER, content(4)));
    }

    @Test
    void foreignKeysRejectMissingUserAndContent() {
        assertThrows(DataIntegrityViolationException.class, () -> new TransactionTemplate(transactions).execute(status ->
                bookmarks.insertIfAbsent(UUID.randomUUID(), content(1), NOW)));
        assertThrows(DataIntegrityViolationException.class, () -> new TransactionTemplate(transactions).execute(status ->
                bookmarks.insertIfAbsent(ACTOR, UUID.randomUUID(), NOW)));
    }

    @Test
    void concurrentInsertHasExactlyOneWinnerAndOneStoredRow() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return insert(ACTOR, 1);
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertEquals(1, first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from bookmarks where user_id=? and content_id=?",
                Integer.class, ACTOR, content(1)));
    }
}
