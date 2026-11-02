package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.QuestionResource;
import com.toeic.backend.questionbank.domain.QuestionVersion;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/** Chuyển đúng version sang DTO không có đáp án/giải thích; caller phải còn transaction và kiểm tra quyền riêng. */
@Component
public class QuestionPromptMapper {
    public QuestionPrompt toPrompt(QuestionVersion version) {
        return buildPrompt(version, resourceId -> true, resourceId -> true);
    }

    /** Q-02 truyền ID đã được lọc quyền từ DB; không lấy các tập này từ request. */
    public QuestionPrompt toPrompt(QuestionVersion version, Set<UUID> questionResourceIds, Set<UUID> groupResourceIds) {
        var allowedQuestionIds = Set.copyOf(questionResourceIds);
        var allowedGroupIds = Set.copyOf(groupResourceIds);
        return buildPrompt(version, allowedQuestionIds::contains, allowedGroupIds::contains);
    }

    private QuestionPrompt buildPrompt(QuestionVersion version, Predicate<UUID> questionResources,
                                       Predicate<UUID> groupResources) {
        version.verifyReferences();
        var question = version.getQuestion();
        var group = version.getGroupVersion();
        var passage = group == null ? null : new QuestionPrompt.Passage(group.getGroupVersionId(),
                group.getVersionNumber(), group.getInstruction(), group.getPassageText(),
                group.getResources().stream().filter(resource -> groupResources.test(resource.getResourceId()))
                        .map(resource -> new QuestionPrompt.Resource(
                        resource.getResourceId(), resource.getDisplayOrder())).toList());
        return new QuestionPrompt(question.getQuestionId(), version.getQuestionVersionId(), version.getVersionNumber(),
                question.getPart().getPartNumber(), question.getTopic() == null ? null : question.getTopic().getName(),
                question.getType().getCode(), version.getDifficulty(), version.getQuestionText(),
                version.getChoices().stream().map(choice -> new QuestionPrompt.Choice(choice.getChoiceId(),
                        choice.getLabel(), choice.getContent(), choice.getDisplayOrder())).toList(),
                version.getResources().stream().map(QuestionResource::getResourceId).filter(questionResources).toList(), passage);
    }
}
