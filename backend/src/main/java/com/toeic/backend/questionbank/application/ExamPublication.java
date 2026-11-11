package com.toeic.backend.questionbank.application;

import java.util.List;
import java.util.UUID;

/** Dữ liệu đã được module assessment kiểm tra trước khi xuất bản vào ngân hàng câu hỏi. */
public record ExamPublication(List<Group> groups,List<Question> questions) {
    public record Group(String key,int partNumber,String instruction,String passageText,UUID audioId,UUID imageId,String transcript) {}
    public record Question(int partNumber,String groupKey,String questionText,List<String> choices,String answer,String explanation,UUID audioId,UUID imageId,String transcript) {}
}
