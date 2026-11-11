package com.toeic.backend.questionbank.application;

import org.springframework.http.HttpStatus;

public class QuestionReadException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    private QuestionReadException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public static QuestionReadException invalidQuery() {
        return new QuestionReadException("VALIDATION_ERROR", "Tham so truy van khong hop le.", HttpStatus.BAD_REQUEST);
    }
    public static QuestionReadException notFound() {
        return new QuestionReadException("QUESTION_NOT_FOUND", "Khong tim thay cau hoi.", HttpStatus.NOT_FOUND);
    }
    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
