package com.toeic.backend.ai.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Nhận diện lời chào ngắn để chatbot phản hồi tại chỗ, không cần gọi dịch vụ AI/RAG. */
public final class BasicGreetingResponder {
    private static final String RESPONSE = "Xin chào! Mình là Trợ lý TOEIC. "
            + "Bạn có thể hỏi mình về ngữ pháp, từ vựng hoặc cách dùng tiếng Anh trong bài thi TOEIC.";
    private static final Set<String> GREETINGS = Set.of(
            "hi", "hi bot", "hello", "hello bot", "hello chatbot", "hey", "hey bot",
            "chao", "chao ban", "chao bot", "chao chatbot", "xin chao", "xin chao ban",
            "xin chao bot", "xin chao chatbot", "chao buoi sang", "chao buoi chieu",
            "chao buoi toi", "good morning", "good afternoon", "good evening"
    );

    private BasicGreetingResponder() { }

    public static Optional<String> respondTo(String message) {
        return GREETINGS.contains(normalize(message)) ? Optional.of(RESPONSE) : Optional.empty();
    }

    private static String normalize(String message) {
        String withoutMarks = Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return withoutMarks.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
