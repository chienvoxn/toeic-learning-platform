package com.toeic.backend.content.application;

import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;

/** Partial update command. Presence is independent of null for nullable fields. */
public record ContentEdit(Set<String> fields, String title, String summary, String body,
                          String categoryCode, String difficulty, List<UUID> assets) {
    public static final Set<String> CATEGORIES = Set.of("GRAMMAR", "VOCAB", "LISTENING", "READING", "STRATEGIES", "DOCS");
    public static final Set<String> FIELDS = Set.of("title", "summary", "body", "categoryCode", "difficulty", "assets");

    public ContentEdit(Set<String> fields, String title, String summary, String body, String categoryCode, String difficulty) {
        this(fields,title,summary,body,categoryCode,difficulty,null);
    }

    public ContentEdit {
        if (fields == null || fields.isEmpty() || !FIELDS.containsAll(fields)) { throw ContentException.invalidPayload(); }
        fields = Set.copyOf(fields);
        if (fields.contains("assets")) {
            if (assets == null || assets.stream().anyMatch(java.util.Objects::isNull)
                    || new HashSet<>(assets).size() != assets.size()) { throw ContentException.invalidPayload(); }
            assets = List.copyOf(assets);
        } else { assets = List.of(); }
        if (fields.contains("title") && (title == null || title.isBlank() || title.length() > 255)
                || fields.contains("body") && body == null
                || fields.contains("categoryCode") && !CATEGORIES.contains(categoryCode == null ? "" : categoryCode)
                || fields.contains("difficulty") && difficulty != null && (difficulty.isBlank() || difficulty.length() > 20)) {
            throw ContentException.invalidPayload();
        }
        for (var value : new String[]{title, summary, body, categoryCode, difficulty}) {
            if (value != null && value.indexOf(0) >= 0) { throw ContentException.invalidPayload(); }
        }
        if (title != null) { title = title.strip(); }
    }

    public boolean has(String field) { return fields.contains(field); }
    public void requireCreate() {
        if (!fields.containsAll(Set.of("title", "body", "categoryCode"))) { throw ContentException.invalidPayload(); }
    }
}
