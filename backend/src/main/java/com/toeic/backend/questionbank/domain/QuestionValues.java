package com.toeic.backend.questionbank.domain;

import java.util.Objects;

final class QuestionValues {
    private QuestionValues() { }
    static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value;
    }
    static String optional(String value, int max, String field) {
        if (value != null && value.length() > max) { throw new IllegalArgumentException("Invalid " + field); }
        return value;
    }
    static int positive(int value, String field) {
        if (value < 1) { throw new IllegalArgumentException(field + " must be positive"); }
        return value;
    }
    static <T> T required(T value, String field) { return Objects.requireNonNull(value, field); }
}
