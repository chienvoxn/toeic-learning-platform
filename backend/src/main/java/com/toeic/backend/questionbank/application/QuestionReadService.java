package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.QuestionAssetMetadata;
import com.toeic.backend.questionbank.infrastructure.QuestionAssetRepository;
import com.toeic.backend.questionbank.infrastructure.QuestionVersionRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Đọc đúng publication snapshot và lọc asset trong cùng transaction trước khi tạo DTO an toàn. */
@Service
@PreAuthorize("@questionReadAccessPolicy.canRead()")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class QuestionReadService {
    private final QuestionVersionRepository versions;
    private final QuestionAssetRepository assets;
    private final QuestionPromptMapper prompts;
    private final QuestionReadAccessPolicy access;
    private final Clock clock;

    public QuestionReadService(QuestionVersionRepository versions, QuestionAssetRepository assets,
                                QuestionPromptMapper prompts, QuestionReadAccessPolicy access, Clock clock) {
        this.versions = versions;
        this.assets = assets;
        this.prompts = prompts;
        this.access = access;
        this.clock = clock;
    }
    public QuestionReadDtos.QuestionPage list(QuestionReadQuery query) {
        var page = versions.findReadable(access.canRead(), OffsetDateTime.now(clock), query.partId(), query.topicId(),
                query.difficulty(), query.pageable());
        return new QuestionReadDtos.QuestionPage(page.getContent().stream().map(QuestionReadDtos.Summary::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
    public QuestionReadDtos.Detail detail(UUID questionId) {
        var version = versions.findReadableByQuestionId(questionId, access.canRead(), OffsetDateTime.now(clock))
                .orElseThrow(QuestionReadException::notFound);
        var questionMedia = assets.findReadableForQuestionVersion(version.getQuestionVersionId());
        var group = version.getGroupVersion();
        List<QuestionAssetMetadata> groupMedia = group == null ? List.of() : assets.findReadableForGroupVersion(group.getGroupVersionId());
        var prompt = prompts.toPrompt(version, questionMedia.stream().map(QuestionAssetMetadata::resourceId).collect(Collectors.toSet()),
                groupMedia.stream().map(QuestionAssetMetadata::resourceId).collect(Collectors.toSet()));
        return QuestionReadDtos.Detail.from(version, prompt, questionMedia, groupMedia);
    }
}
