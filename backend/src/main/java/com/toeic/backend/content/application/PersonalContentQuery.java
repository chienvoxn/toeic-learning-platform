package com.toeic.backend.content.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public record PersonalContentQuery(int page, int size, String sort, Kind kind) {
    public enum Kind {
        BOOKMARKS("createdAt"), HISTORY("lastAccessedAt");
        private final String timestamp;
        Kind(String timestamp) { this.timestamp = timestamp; }
    }

    public PersonalContentQuery {
        ContentQuery.validatePage(page, size);
        if (kind == null || sort == null) { throw ContentException.invalidQuery(); }
        var fields = sort.split(",", -1);
        if (fields.length != 2 || !(fields[0].equals(kind.timestamp) || fields[0].equals("contentId"))
                || !(fields[1].equals("asc") || fields[1].equals("desc"))) { throw ContentException.invalidQuery(); }
    }

    public static PersonalContentQuery parse(Map<String, List<String>> params, Kind kind) {
        ContentQuery.validateParameters(params, Set.of("page", "size", "sort"));
        try {
            return new PersonalContentQuery(Integer.parseInt(value(params, "page", "0")),
                    Integer.parseInt(value(params, "size", "20")), value(params, "sort", kind.timestamp + ",desc"), kind);
        } catch (NumberFormatException exception) { throw ContentException.invalidQuery(); }
    }

    private static String value(Map<String, List<String>> params, String name, String fallback) {
        return params.containsKey(name) ? params.get(name).getFirst() : fallback;
    }

    public void requireKind(Kind expected) { if (kind != expected) { throw ContentException.invalidQuery(); } }

    public PageRequest pageable() {
        var fields = sort.split(",", -1);
        var property = fields[0].equals("contentId") ? "id.contentId" : kind.timestamp;
        var order = Sort.by(Sort.Direction.fromString(fields[1]), property);
        if (!fields[0].equals("contentId")) { order = order.and(Sort.by("id.contentId")); }
        return PageRequest.of(page, size, order);
    }
}
