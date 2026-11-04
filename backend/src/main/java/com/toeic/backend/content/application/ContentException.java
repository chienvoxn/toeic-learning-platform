package com.toeic.backend.content.application;

import org.springframework.http.HttpStatus;

public class ContentException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    private ContentException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public static ContentException invalidQuery() {
        return new ContentException("VALIDATION_ERROR", "Tham so truy van khong hop le.", HttpStatus.BAD_REQUEST);
    }
    public static ContentException notFound() {
        return new ContentException("CONTENT_NOT_FOUND", "Khong tim thay hoc lieu.", HttpStatus.NOT_FOUND);
    }
    public static ContentException bookmarkExists() {
        return new ContentException("BOOKMARK_EXISTS", "Hoc lieu da duoc luu.", HttpStatus.CONFLICT);
    }
    public static ContentException bookmarkNotFound() {
        return new ContentException("BOOKMARK_NOT_FOUND", "Khong tim thay bai da luu.", HttpStatus.NOT_FOUND);
    }
    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
