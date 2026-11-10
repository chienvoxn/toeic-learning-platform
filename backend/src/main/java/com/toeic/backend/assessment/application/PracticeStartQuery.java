package com.toeic.backend.assessment.application;

import java.util.*;

public record PracticeStartQuery(List<Integer> partNumbers, int questionCount, Integer topicId, String difficulty) {
    public PracticeStartQuery {
        if (partNumbers == null || partNumbers.isEmpty() || partNumbers.size()>7
                || partNumbers.stream().anyMatch(p -> p==null || p<1 || p>7)
                || partNumbers.stream().distinct().count()!=partNumbers.size()
                || questionCount<1 || questionCount>200 || topicId!=null && topicId<1
                || difficulty!=null && !Set.of("EASY","MEDIUM","HARD").contains(difficulty)) throw PracticeException.invalid();
        partNumbers=partNumbers.stream().sorted().toList();
    }
}
