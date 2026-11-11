package com.toeic.backend.content.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public record ContentQuery(Integer categoryId, String difficulty, int page, int size, String sort, String q) {
    private static final Set<String> PARAMETERS = Set.of("categoryId", "difficulty", "page", "size", "sort", "q");
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "title", "currentPublishedVersion.title", "publishedAt", "currentPublishedVersion.publishedAt",
            "difficulty", "difficulty", "contentId", "contentId");

    public ContentQuery(Integer categoryId, String difficulty, int page, int size, String sort) {
        this(categoryId, difficulty, page, size, sort, null);
    }

    public ContentQuery {
        validatePage(page, size);
        if (categoryId != null && categoryId < 1
                || difficulty != null && (difficulty.isBlank() || difficulty.length() > 20)) {
            throw ContentException.invalidQuery();
        }
        validateSort(sort);
        if (q != null) {
            q = q.replaceAll("(?U)\\s+", " ").strip();
            if (q.indexOf(0) >= 0 || q.codePointCount(0, q.length()) > 200) { throw ContentException.invalidQuery(); }
            if (q.isEmpty()) { q = null; }
        }
    }

    public static ContentQuery parse(Map<String, List<String>> params) {
        validateParameters(params, PARAMETERS);
        try {
            String category = value(params, "categoryId", null);
            return new ContentQuery(category == null ? null : Integer.valueOf(category),
                    value(params, "difficulty", null), Integer.parseInt(value(params, "page", "0")),
                    Integer.parseInt(value(params, "size", "20")), value(params, "sort", "publishedAt,desc"),
                    value(params, "q", null));
        } catch (NumberFormatException exception) {
            throw ContentException.invalidQuery();
        }
    }

    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw ContentException.invalidQuery();
        }
    }

    static void validateParameters(Map<String, List<String>> params, Set<String> allowed) {
        if (!allowed.containsAll(params.keySet()) || params.values().stream().anyMatch(values -> values.size() != 1)) {
            throw ContentException.invalidQuery();
        }
    }

    public String searchPattern() {
        return q == null ? null : "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static String value(Map<String, List<String>> params, String name, String fallback) {
        return params.containsKey(name) ? params.get(name).getFirst() : fallback;
    }

    private static void validateSort(String sort) {
        if (sort == null) { throw ContentException.invalidQuery(); }
        var fields = sort.split(",", -1);
        if (fields.length != 2 || !SORT_FIELDS.containsKey(fields[0])
                || !(fields[1].equals("asc") || fields[1].equals("desc"))) {
            throw ContentException.invalidQuery();
        }
    }

    public PageRequest pageable() {
        var fields = sort.split(",", -1);
        var result = Sort.by(Sort.Direction.fromString(fields[1]), SORT_FIELDS.get(fields[0]));
        if (!fields[0].equals("contentId")) { result = result.and(Sort.by("contentId")); }
        return PageRequest.of(page, size, result);
    }
}
