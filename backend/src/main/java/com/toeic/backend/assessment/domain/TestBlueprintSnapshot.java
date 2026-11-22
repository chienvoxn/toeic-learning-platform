package com.toeic.backend.assessment.domain;

import java.util.*;

/** Cấu hình blueprint version được sao chép vào đề/attempt; không suy từ dữ liệu demo. */
public record TestBlueprintSnapshot(UUID blueprintId, String code, int version, String name, int durationSeconds,
                                    List<Part> parts) {
    public TestBlueprintSnapshot { parts=List.copyOf(parts); }
    public record Part(int partNumber,int questionCount,Integer groupCount,Integer minGroupSize,Integer maxGroupSize,
                       boolean requireAudio,boolean requireImage) { }
    public int totalQuestions() { return parts.stream().mapToInt(Part::questionCount).sum(); }
}
