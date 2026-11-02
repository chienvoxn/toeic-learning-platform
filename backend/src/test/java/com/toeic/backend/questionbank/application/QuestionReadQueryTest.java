package com.toeic.backend.questionbank.application;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class QuestionReadQueryTest {
    @Test
    void defaultsAreBoundedAndHaveAStableSecondaryOrder() {
        var query = QuestionReadQuery.parse(Map.of());
        assertEquals(0, query.page());
        assertEquals(20, query.size());
        assertNull(query.partId());
        assertNull(query.topicId());
        var orders = query.pageable().getSort().toList();
        assertEquals(List.of("publishedAt", "question.questionId"), orders.stream().map(Sort.Order::getProperty).toList());
        assertEquals(Sort.Direction.DESC, orders.getFirst().getDirection());
        assertEquals(Sort.Direction.ASC, orders.getLast().getDirection());
    }

    @Test
    void supportsRealTaxonomyIdsAndSchemaDifficultyWithoutAssumingAnEnum() {
        var query = QuestionReadQuery.parse(Map.of("partId", List.of("123"), "topicId", List.of("456"),
                "difficulty", List.of("CUSTOM"), "page", List.of("2"), "size", List.of("100"),
                "sort", List.of("questionId,desc")));
        assertEquals(123, query.partId());
        assertEquals(456, query.topicId());
        assertEquals("CUSTOM", query.difficulty());
        assertEquals(200, query.pageable().getOffset());
        assertEquals(1, query.pageable().getSort().toList().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"partId,asc", "topicId,desc", "difficulty,asc", "publishedAt,desc", "questionId,asc"})
    void acceptsOnlyNamedPublicSortFields(String sort) {
        assertDoesNotThrow(() -> QuestionReadQuery.parse(Map.of("sort", List.of(sort))).pageable());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=x", "page=2147483647", "size=0", "size=101", "size=x",
            "partId=0", "partId=-1", "partId=bad", "topicId=0", "topicId=bad", "difficulty=",
            "difficulty=123456789012345678901", "status=DRAFT", "versionId=anything", "partNumber=1",
            "sort=explanation,asc", "sort=isCorrect,asc", "sort=question.createdBy,asc", "sort=publishedAt,sideways",
            "sort=questionId", "sort=questionId,asc,difficulty", "sort=questionId) desc,--"})
    void rejectsInvalidOrUnsupportedInput(String parameter) {
        var parts = parameter.split("=", 2);
        var error = assertThrows(QuestionReadException.class,
                () -> QuestionReadQuery.parse(Map.of(parts[0], List.of(parts[1]))));
        assertEquals("VALIDATION_ERROR", error.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void rejectsDuplicateParametersAndDirectConstructionOutsideBounds() {
        assertThrows(QuestionReadException.class,
                () -> QuestionReadQuery.parse(Map.of("partId", List.of("1", "2"))));
        assertThrows(QuestionReadException.class,
                () -> QuestionReadQuery.parse(Map.of("sort", List.of("questionId,asc", "difficulty,desc"))));
        assertThrows(QuestionReadException.class, () -> new QuestionReadQuery(null, null, null, 0, 101, "questionId,asc"));
        assertThrows(QuestionReadException.class, () -> new QuestionReadQuery(null, null, null, 0, 20, null));
    }
}
