package com.toeic.backend.identity.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.toeic.backend.identity.application.ProfileException;
import com.toeic.backend.identity.application.ProfileUpdate;
import tools.jackson.databind.JsonNode;

/** Allowlist cục bộ, không thay đổi chính sách deserialize của API hiện hữu. */
public class UpdateProfileRequest {
    private String fullName;
    private boolean fullNamePresent;
    private Integer targetScore;
    private boolean targetScorePresent;

    @JsonSetter("fullName")
    public void fullName(JsonNode value) {
        if (value == null || !value.isString()) { throw ProfileException.validation(); }
        this.fullName = value.asString();
        this.fullNamePresent = true;
    }

    @JsonSetter("targetScore")
    public void targetScore(JsonNode value) {
        this.targetScorePresent = true;
        if (value == null || value.isNull()) { this.targetScore = null; return; }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) { throw ProfileException.validation(); }
        this.targetScore = value.asInt();
    }

    @JsonAnySetter
    public void rejectUnknown(String field, JsonNode value) {
        throw ProfileException.validation();
    }

    public ProfileUpdate toCommand() {
        return new ProfileUpdate(fullNamePresent, fullName, targetScorePresent, targetScore);
    }
}
