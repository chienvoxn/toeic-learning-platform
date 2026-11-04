package com.toeic.backend.content.application;

import com.toeic.backend.content.domain.LearningContent;
import com.toeic.backend.content.domain.LearningContentVersion;
import com.toeic.backend.content.infrastructure.*;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Authorizes the actor, scopes queries, and serializes authoring with parent-before-version locks. */
@Service
@PreAuthorize("hasAnyAuthority('content:manage:self','content:manage:any') and @contentAccessPolicy.isActor(#actorId)")
public class ContentManagementService {
    private final ContentRepository contents;
    private final ContentVersionRepository versions;
    private final ContentCategoryRepository categories;
    private final ContentMediaRepository media;
    private final ContentAssetRepository assets;
    private final ContentMediaReuseService reuse;
    private final ContentAccessPolicy access;
    private final Clock clock;

    public ContentManagementService(ContentRepository contents, ContentVersionRepository versions,
            ContentCategoryRepository categories, ContentMediaRepository media, ContentAssetRepository assets,
            ContentMediaReuseService reuse, ContentAccessPolicy access, Clock clock) {
        this.contents = contents;
        this.versions = versions;
        this.categories = categories;
        this.media = media;
        this.assets = assets;
        this.reuse = reuse;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public ContentManagementDtos.Detail create(UUID actorId, ContentEdit edit) {
        edit.requireCreate();
        var category = categories.findByCode(edit.categoryCode()).orElseThrow(ContentException::invalidPayload);
        var links = requestedLinks(edit);
        // No shared parent/version exists on create; validate before inserting the new aggregate.
        reuse.validate(actorId,links.stream().map(ContentAssetRepository.Link::mediaId).toList(),List.of(),false);
        var now = OffsetDateTime.now(clock);
        var content = contents.saveAndFlush(new LearningContent(actorId,category,edit.title(),edit.summary(),
                edit.body(),edit.difficulty(),now));
        var version = versions.saveAndFlush(new LearningContentVersion(content.getContentId(),1,actorId,edit.title(),
                edit.summary(),edit.body(),now));
        if (!links.isEmpty()) { assets.replace(version.getContentVersionId(),links); }
        return detail(content,List.of(version));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ContentManagementDtos.Page list(UUID actorId, ContentManagementQuery query) {
        var rows = contents.findManaged(actorId,access.canManageAny(),query.categoryCode(),query.status(),query.pageable());
        var drafts = rows.isEmpty() ? List.<LearningContentVersion>of()
                : versions.findDrafts(rows.getContent().stream().map(LearningContent::getContentId).toList());
        return new ContentManagementDtos.Page(rows.getContent().stream().map(content -> summary(content,
                viewDraft(drafts.stream().filter(v -> v.getContentId().equals(content.getContentId())).toList()))).toList(),
                rows.getNumber(),rows.getSize(),rows.getTotalElements(),rows.getTotalPages());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ContentManagementDtos.Detail get(UUID actorId, UUID id) {
        var content = contents.findManagedById(id,actorId,access.canManageAny()).orElseThrow(ContentException::notFound);
        return detail(content,versions.findAllForContent(id));
    }

    @Transactional
    public ContentManagementDtos.Detail update(UUID actorId, UUID id, ContentEdit edit) {
        var content = contents.lockManaged(id,actorId,access.canManageAny()).orElseThrow(ContentException::notFound);
        if (!List.of("DRAFT","PUBLISHED").contains(content.getStatus())) { throw ContentException.stateConflict(); }
        boolean metadata = edit.has("categoryCode") || edit.has("difficulty");
        if (metadata && !"DRAFT".equals(content.getStatus())) { throw ContentException.metadataImmutable(); }
        var category = edit.has("categoryCode")
                ? categories.findByCode(edit.categoryCode()).orElseThrow(ContentException::invalidPayload) : content.getCategory();
        var all = versions.lockAllForContent(id);
        var pointer = content.getCurrentPublishedVersion();
        if ("PUBLISHED".equals(content.getStatus())) {
            if (pointer == null || !id.equals(pointer.getContentId()) || !"PUBLISHED".equals(pointer.getStatus())
                    || pointer.getPublishedAt() == null || pointer.getArchivedAt() != null) {
                throw ContentException.stateConflict();
            }
        } else if (pointer != null) { throw ContentException.stateConflict(); }
        var draft = workingDraft(all);
        boolean cloning = draft == null;
        if (cloning && pointer == null) { throw ContentException.stateConflict(); }
        var source = assets.findAll(cloning ? pointer.getContentVersionId() : draft.getContentVersionId());
        var target = edit.has("assets") ? requestedLinks(edit) : source;
        reuse.validate(actorId,target.stream().map(ContentAssetRepository.Link::mediaId).toList(),
                source.stream().map(ContentAssetRepository.Link::mediaId).toList(),cloning);
        if (metadata) { content.editDraftMetadata(category,edit.has("difficulty") ? edit.difficulty() : content.getDifficulty()); }
        var now = OffsetDateTime.now(clock);
        if (draft == null) {
            if (pointer == null) { throw ContentException.stateConflict(); }
            int maximum = all.stream().mapToInt(LearningContentVersion::getVersionNumber).max().orElse(0);
            if (maximum == Integer.MAX_VALUE) { throw ContentException.stateConflict(); }
            draft = versions.saveAndFlush(new LearningContentVersion(id,maximum+1,actorId,pointer.getTitle(),
                    pointer.getSummary(),pointer.getBody(),now));
            all = new java.util.ArrayList<>(all);
            all.add(draft);
        }
        draft.replaceDraftContent(edit.has("title") ? edit.title() : draft.getTitle(),
                edit.has("summary") ? edit.summary() : draft.getSummary(),
                edit.has("body") ? edit.body() : draft.getBody(),now);
        content.touch(now);
        if (cloning || edit.has("assets")) { assets.replace(draft.getContentVersionId(),target); }
        return detail(content,all);
    }

    @Transactional
    public void archive(UUID actorId, UUID id) {
        var content = contents.lockManaged(id,actorId,access.canManageAny()).orElseThrow(ContentException::notFound);
        content.archive(OffsetDateTime.now(clock));
    }

    private static List<ContentAssetRepository.Link> requestedLinks(ContentEdit edit) {
        return java.util.stream.IntStream.range(0,edit.assets().size())
                .mapToObj(i -> new ContentAssetRepository.Link(edit.assets().get(i),i+1)).toList();
    }

    private static LearningContentVersion workingDraft(List<LearningContentVersion> all) {
        var drafts = all.stream().filter(v -> "DRAFT".equals(v.getStatus())).toList();
        // Existing ambiguous data is not silently selected, archived, or overwritten.
        if (drafts.size() > 1) { throw ContentException.stateConflict(); }
        var draft = drafts.isEmpty() ? null : drafts.getFirst();
        if (draft != null && (draft.getPublishedAt() != null || draft.getArchivedAt() != null)) {
            throw ContentException.stateConflict();
        }
        return draft;
    }

    private static LearningContentVersion viewDraft(List<LearningContentVersion> all) {
        var drafts = all.stream().filter(v -> "DRAFT".equals(v.getStatus())).toList();
        // Management reads remain available for inspecting legacy conflicts; do not select a canonical draft.
        return drafts.size() == 1 ? drafts.getFirst() : null;
    }

    private static ContentManagementDtos.Summary summary(LearningContent content, LearningContentVersion draft) {
        var published = content.getCurrentPublishedVersion();
        return new ContentManagementDtos.Summary(content.getContentId(),ContentDtos.Category.from(content.getCategory()),
                draft != null ? draft.getTitle() : published == null ? null : published.getTitle(),content.getDifficulty(),
                content.getStatus(),content.getVisibility(),published == null ? null : published.getContentVersionId(),
                draft == null ? null : draft.getContentVersionId(),content.getCreatedAt(),content.getUpdatedAt());
    }

    private ContentManagementDtos.Detail detail(LearningContent content, List<LearningContentVersion> all) {
        return new ContentManagementDtos.Detail(summary(content,viewDraft(all)),all.stream().map(version ->
                new ContentManagementDtos.Version(version.getContentVersionId(),version.getVersionNumber(),version.getStatus(),
                        version.getTitle(),version.getSummary(),version.getBody(),version.getCreatedAt(),version.getUpdatedAt(),
                        version.getPublishedAt(),version.getArchivedAt(),media.findReadable(version.getContentVersionId(),
                                access.canReadMemberContent()).stream().map(ContentDtos.Media::from).toList())).toList());
    }
}
