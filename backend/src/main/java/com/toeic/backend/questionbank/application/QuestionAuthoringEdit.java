package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.QuestionChoiceDraft;
import java.util.*;

public record QuestionAuthoringEdit(Set<String> fields,Integer partId,Integer topicId,Integer questionTypeId,
        UUID groupVersionId,String questionText,String explanation,String difficulty,String groupType,
        String instruction,String passageText,List<QuestionChoiceDraft> choices,List<Asset> assets) {
    public QuestionAuthoringEdit {
        fields=Set.copyOf(fields);
        choices=choices==null?null:List.copyOf(choices);
        assets=assets==null?null:List.copyOf(assets);
    }
    public boolean has(String field) { return fields.contains(field); }
    public record Asset(UUID mediaId,String transcript) {
        public Asset { if(mediaId==null) throw QuestionAuthoringException.invalid(); }
        @Override public String toString() { return "QuestionAuthoringAsset[redacted]"; }
    }
    @Override public String toString() { return "QuestionAuthoringEdit[redacted]"; }
}
