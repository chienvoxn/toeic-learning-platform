package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.infrastructure.QuestionVersionRepository;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Cổng nội bộ đọc key của version đã pin; caller phải giữ khóa attempt. Không có HTTP route hay phụ thuộc token. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class PinnedQuestionGrading {
    private final QuestionVersionRepository versions;
    public PinnedQuestionGrading(QuestionVersionRepository versions){this.versions=versions;}
    public PracticeQuestionCatalog.MarkingKey key(UUID questionVersionId,UUID groupVersionId){
        var version=versions.findById(questionVersionId).orElseThrow(()->new PracticeQuestionCatalog.Unavailable(false));
        var group=version.getGroupVersion();
        if(!Objects.equals(groupVersionId,group==null?null:group.getGroupVersionId()))throw new PracticeQuestionCatalog.Unavailable(false);
        var key=version.answerKey();if(key.correctChoiceIds().size()!=1)throw new PracticeQuestionCatalog.Unavailable(false);
        var part=version.getQuestion().getPart();
        return new PracticeQuestionCatalog.MarkingKey(version.getQuestion().getQuestionId(),questionVersionId,part.getPartId(),part.getPartNumber(),key.correctChoiceIds().iterator().next(),key.explanation());
    }
}
