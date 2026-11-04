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
    public static ContentException invalidPayload() {
        return new ContentException("VALIDATION_ERROR", "Du lieu hoc lieu khong hop le.", HttpStatus.BAD_REQUEST);
    }
    public static ContentException stateConflict() {
        return new ContentException("CONTENT_STATE_CONFLICT", "Trang thai hoc lieu khong cho phep thao tac.", HttpStatus.CONFLICT);
    }
    public static ContentException metadataImmutable() {
        return new ContentException("CONTENT_METADATA_IMMUTABLE", "Chi sua category/difficulty khi hoc lieu la DRAFT.", HttpStatus.CONFLICT);
    }
    public static ContentException mediaNotFound() {
        return new ContentException("MEDIA_NOT_FOUND", "Khong tim thay media duoc phep su dung.", HttpStatus.NOT_FOUND);
    }
    public static ContentException mediaReuseConflict() {
        return new ContentException("CONTENT_MEDIA_REUSE_CONFLICT", "Khong the tai su dung toan bo media cua phien ban.", HttpStatus.CONFLICT);
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
