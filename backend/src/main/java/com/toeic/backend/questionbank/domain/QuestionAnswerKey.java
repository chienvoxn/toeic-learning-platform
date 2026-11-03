package com.toeic.backend.questionbank.domain;

import java.util.Set;
import java.util.UUID;

/** Snapshot nội bộ của đáp án/giải thích, không dùng làm response trước khi được phép. */
public record QuestionAnswerKey(UUID questionVersionId, Set<UUID> correctChoiceIds, String explanation) {
    public QuestionAnswerKey { correctChoiceIds = Set.copyOf(correctChoiceIds); }
    @Override public String toString() { return "QuestionAnswerKey[redacted]"; }
}
