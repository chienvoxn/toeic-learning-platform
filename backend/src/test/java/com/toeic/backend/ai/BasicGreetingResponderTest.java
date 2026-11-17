package com.toeic.backend.ai;

import com.toeic.backend.ai.domain.BasicGreetingResponder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BasicGreetingResponderTest {
    @Test void recognizesCommonVietnameseAndEnglishGreetings() {
        assertTrue(BasicGreetingResponder.respondTo("Chào bạn!").isPresent());
        assertTrue(BasicGreetingResponder.respondTo("HELLO, chatbot!").isPresent());
        assertTrue(BasicGreetingResponder.respondTo("  good   morning ").isPresent());
    }

    @Test void doesNotTreatLearningQuestionsAsGreetings() {
        assertTrue(BasicGreetingResponder.respondTo("Xin chào, giải thích thì hiện tại hoàn thành").isEmpty());
        assertTrue(BasicGreetingResponder.respondTo("What does hello mean?").isEmpty());
    }
}
