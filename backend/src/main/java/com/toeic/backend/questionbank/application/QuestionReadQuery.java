package com.toeic.backend.questionbank.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Kiểm tra đầu vào; không nhận status, version hoặc cờ quyền từ client. */
public record QuestionReadQuery(Integer partId, Integer topicId, String difficulty, int page, int size, String sort) {
    private static final Set<String> PARAMETERS = Set.of("partId", "topicId", "difficulty", "page", "size", "sort");
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "questionId", "question.questionId", "partId", "question.part.partId", "topicId", "question.topic.topicId",
            "difficulty", "difficulty", "publishedAt", "publishedAt");

    public QuestionReadQuery {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || partId != null && partId < 1 || topicId != null && topicId < 1
                || difficulty != null && (difficulty.isBlank() || difficulty.length() > 20)) {
            throw QuestionReadException.invalidQuery();
        }
        validateSort(sort);
    }
    public static QuestionReadQuery parse(Map<String, List<String>> params) {
        if (!PARAMETERS.containsAll(params.keySet()) || params.values().stream().anyMatch(values -> values.size() != 1)) {
            throw QuestionReadException.invalidQuery();
        }
        try {
            return new QuestionReadQuery(optionalId(params, "partId"), optionalId(params, "topicId"),
                    value(params, "difficulty", null), Integer.parseInt(value(params, "page", "0")),
                    Integer.parseInt(value(params, "size", "20")), value(params, "sort", "publishedAt,desc"));
        } catch (NumberFormatException exception) {
            throw QuestionReadException.invalidQuery();
        }
    }
    private static Integer optionalId(Map<String, List<String>> params, String field) {
        String value = value(params, field, null);
        return value == null ? null : Integer.valueOf(value);
    }
    private static String value(Map<String, List<String>> params, String name, String fallback) {
        return params.containsKey(name) ? params.get(name).getFirst() : fallback;
    }
    private static void validateSort(String sort) {
        if (sort == null) { throw QuestionReadException.invalidQuery(); }
        var parts = sort.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.containsKey(parts[0])
                || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
            throw QuestionReadException.invalidQuery();
        }
    }
    public PageRequest pageable() {
        var parts = sort.split(",", -1);
        var result = Sort.by(Sort.Direction.fromString(parts[1]), SORT_FIELDS.get(parts[0]));
        if (!parts[0].equals("questionId")) { result = result.and(Sort.by("question.questionId")); }
        return PageRequest.of(page, size, result);
    }
}
