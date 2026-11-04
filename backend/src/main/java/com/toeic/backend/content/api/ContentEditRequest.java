package com.toeic.backend.content.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.toeic.backend.content.application.ContentEdit;
import com.toeic.backend.content.application.ContentException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Local allowlist, with strict types and explicit null; no global Jackson contract change. */
public class ContentEditRequest {
    private final Map<String, String> values = new HashMap<>();
    private final Set<String> fields = new HashSet<>();
    private List<UUID> assets;

    @JsonAnySetter
    public void field(String name, JsonNode value) {
        if (!ContentEdit.FIELDS.contains(name) || !fields.add(name)) { throw ContentException.invalidPayload(); }
        if (name.equals("assets")) {
            if (value == null || !value.isArray()) { throw ContentException.invalidPayload(); }
            assets = new ArrayList<>();
            for (var asset : value) {
                if (!asset.isObject() || asset.size() != 1 || !asset.has("mediaId") || !asset.get("mediaId").isString()) {
                    throw ContentException.invalidPayload();
                }
                try {
                    var raw = asset.get("mediaId").asString();
                    var id = UUID.fromString(raw);
                    if (!id.toString().equalsIgnoreCase(raw)) { throw ContentException.invalidPayload(); }
                    assets.add(id);
                } catch (IllegalArgumentException exception) { throw ContentException.invalidPayload(); }
            }
            return;
        }
        if (value != null && !value.isNull() && !value.isString()) {
            throw ContentException.invalidPayload();
        }
        values.put(name, value == null || value.isNull() ? null : value.asString());
    }

    public ContentEdit command() {
        return new ContentEdit(fields, values.get("title"), values.get("summary"), values.get("body"),
                values.get("categoryCode"), values.get("difficulty"),assets);
    }
}
