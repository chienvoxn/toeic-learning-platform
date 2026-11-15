package com.toeic.backend.ai.application;

public class ChatFailure extends RuntimeException {
    public final int status;
    public final String code;
    public ChatFailure(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public static ChatFailure missing() { return new ChatFailure(404,"AI_NOT_FOUND","Không tìm thấy hội thoại."); }
}
