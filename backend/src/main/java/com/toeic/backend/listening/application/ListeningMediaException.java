package com.toeic.backend.listening.application;

/** Lỗi nghiệp vụ cho luồng quản lý media MinIO/S3 và truy xuất media/transcript của bài thi Listening. */
public class ListeningMediaException extends RuntimeException {
    private final int status;
    private final String code;

    public ListeningMediaException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ListeningMediaException invalid() {
        return new ListeningMediaException(400, "VALIDATION_ERROR", "Du lieu media khong hop le.");
    }

    public static ListeningMediaException mediaNotFound() {
        return new ListeningMediaException(404, "MEDIA_NOT_FOUND", "Khong tim thay tai nguyen media.");
    }

    public static ListeningMediaException attemptNotFound() {
        return new ListeningMediaException(404, "ATTEMPT_NOT_FOUND", "Khong tim thay phien lam bai.");
    }

    public static ListeningMediaException storageUnavailable() {
        return new ListeningMediaException(503, "MEDIA_STORAGE_UNAVAILABLE", "Dich vu luu tru media (MinIO/S3) tam thoi khong kha dung.");
    }
}
