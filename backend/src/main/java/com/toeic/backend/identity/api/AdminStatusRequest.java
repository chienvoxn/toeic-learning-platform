package com.toeic.backend.identity.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.toeic.backend.identity.application.AdminUserException;
import tools.jackson.databind.JsonNode;
import java.util.Set;

public class AdminStatusRequest {
    private String status;
    @JsonSetter("status") public void status(JsonNode value) {
        if (status!=null || value==null || !value.isString() || !Set.of("ACTIVE","LOCKED").contains(value.asString())) throw AdminUserException.invalid();
        status=value.asString();
    }
    @JsonAnySetter public void unknown(String name,JsonNode value) { throw AdminUserException.invalid(); }
    public String status() { if(status==null) throw AdminUserException.invalid(); return status; }
}
