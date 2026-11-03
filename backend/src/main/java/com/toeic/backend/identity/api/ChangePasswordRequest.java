package com.toeic.backend.identity.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.toeic.backend.identity.application.PasswordChange;
import com.toeic.backend.identity.application.ProfileException;
import tools.jackson.databind.JsonNode;

public class ChangePasswordRequest {
    private String currentPassword;
    private String newPassword;

    @JsonSetter("currentPassword")
    public void currentPassword(JsonNode value) {
        if (value == null || !value.isString()) { throw ProfileException.validation(); }
        currentPassword = value.asString();
    }

    @JsonSetter("newPassword")
    public void newPassword(JsonNode value) {
        if (value == null || !value.isString()) { throw ProfileException.validation(); }
        newPassword = value.asString();
    }

    @JsonAnySetter
    public void rejectUnknown(String field, JsonNode value) { throw ProfileException.validation(); }

    public PasswordChange toCommand() { return new PasswordChange(currentPassword, newPassword); }

    @Override
    public String toString() { return "ChangePasswordRequest[redacted]"; }
}
