package com.toeic.backend.assessment.application;

import java.util.*;

/** Cấu hình sản phẩm Section, không khẳng định cấu trúc/thời lượng TOEIC chính thức. */
public record SectionStartQuery(String type,int questionCount,int durationMinutes) {
    public SectionStartQuery {
        if(type==null||!Set.of("LISTENING","READING").contains(type)||questionCount<1||questionCount>100
                ||durationMinutes<1||durationMinutes>120)throw PracticeException.invalid();
    }
    public List<Integer> partNumbers() { return type.equals("LISTENING")?List.of(1,2,3,4):List.of(5,6,7); }
    public PracticeStartQuery selection() { return new PracticeStartQuery(partNumbers(),questionCount,null,null); }
}
