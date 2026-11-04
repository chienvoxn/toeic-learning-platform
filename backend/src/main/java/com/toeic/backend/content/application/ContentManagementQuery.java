package com.toeic.backend.content.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public record ContentManagementQuery(String categoryCode, String status, int page, int size, String sort) {
    private static final Set<String> SORTS = Set.of("createdAt", "updatedAt", "contentId", "status");
    public ContentManagementQuery {
        ContentQuery.validatePage(page, size);
        if (categoryCode != null && !ContentEdit.CATEGORIES.contains(categoryCode)
                || status != null && !Set.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(status)) {
            throw ContentException.invalidQuery();
        }
        var parts = sort == null ? new String[0] : sort.split(",", -1);
        if (parts.length != 2 || !SORTS.contains(parts[0]) || !Set.of("asc", "desc").contains(parts[1])) {
            throw ContentException.invalidQuery();
        }
    }
    public static ContentManagementQuery parse(Map<String, List<String>> parameters) {
        ContentQuery.validateParameters(parameters, Set.of("categoryCode", "status", "page", "size", "sort"));
        try {
            return new ContentManagementQuery(value(parameters,"categoryCode",null), value(parameters,"status",null),
                    Integer.parseInt(value(parameters,"page","0")), Integer.parseInt(value(parameters,"size","20")),
                    value(parameters,"sort","updatedAt,desc"));
        } catch (NumberFormatException exception) { throw ContentException.invalidQuery(); }
    }
    private static String value(Map<String, List<String>> parameters, String name, String fallback) {
        return parameters.containsKey(name) ? parameters.get(name).getFirst() : fallback;
    }
    public PageRequest pageable() {
        var parts = sort.split(",");
        var ordering = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
        if (!parts[0].equals("contentId")) { ordering = ordering.and(Sort.by("contentId")); }
        return PageRequest.of(page, size, ordering);
    }
}
