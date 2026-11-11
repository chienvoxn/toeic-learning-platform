package com.toeic.backend.assessment.api;

import com.fasterxml.jackson.annotation.*;
import com.toeic.backend.assessment.application.PracticeException;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Chỉ nhận selectedChoiceId; null tường minh xóa lựa chọn, omitted không hợp lệ. */
public class SavePracticeAnswerRequest {
    private boolean seen;
    private UUID choice;
    @JsonSetter("selectedChoiceId")
    public void selected(JsonNode value) {
        if (seen) throw PracticeException.invalid();
        seen = true;
        if (value == null || value.isNull()) return;
        if (!value.isString() || !value.asString().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw PracticeException.invalid();
        }
        choice = UUID.fromString(value.asString());
    }
    @JsonAnySetter public void unknown(String name, JsonNode value) { throw PracticeException.invalid(); }
    public UUID choiceId() {
        if (!seen) throw PracticeException.invalid();
        return choice;
    }
}
