package com.toeic.backend.ai.application;

import java.util.List;
import java.util.UUID;

public final class ChatContracts {
    private ChatContracts() {}
    public record Conversation(UUID id, String title, String updatedAt) {}
    public record Message(UUID id, String role, String content, String createdAt, Answer answer) {}
    public record Citation(UUID documentId, UUID chunkId, UUID contentId, UUID contentVersionId,
                           String title, String excerpt, Integer page) {}
    public record Answer(String answer, boolean grounded, String warning, List<Citation> citations) {}
    public record History(String role, String content) {}
    public record Evidence(UUID documentId, UUID contentId, UUID contentVersionId) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteCitation(UUID documentId, UUID chunkId, String title, String excerpt, Integer page) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteUsage(String provider, String chatModel, int inputTokens, int outputTokens,
                              int totalTokens, int latencyMs) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteAnswer(String answer, boolean grounded, String warning,
                               List<RemoteCitation> citations, RemoteUsage usage) {}
    public static final String NO_SOURCE = "Không tìm thấy tài liệu tham khảo trong hệ thống.";
}
