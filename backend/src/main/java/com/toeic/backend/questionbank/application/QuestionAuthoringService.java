package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.domain.QuestionChoiceDraft;
import com.toeic.backend.questionbank.infrastructure.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.toeic.backend.questionbank.infrastructure.QuestionAuthoringRepository.*;

@Service
@PreAuthorize("hasAnyAuthority('question:manage:self','question:manage:any') and @questionAuthoringAccess.isActor(#actorId)")
public class QuestionAuthoringService {
    private final QuestionAuthoringRepository data;
    private final QuestionVersionRepository questions;
    private final QuestionGroupRepository groups;
    private final QuestionAuthoringSnapshotMapper mapper;
    private final QuestionMediaReuseService media;
    private final QuestionAuthoringAccess access;
    private final EntityManager em;
    private final Clock clock;
    public QuestionAuthoringService(QuestionAuthoringRepository data,QuestionVersionRepository questions,QuestionGroupRepository groups,
            QuestionAuthoringSnapshotMapper mapper,QuestionMediaReuseService media,QuestionAuthoringAccess access,EntityManager em,Clock clock) {
        this.data=data;this.questions=questions;this.groups=groups;this.mapper=mapper;this.media=media;this.access=access;this.em=em;this.clock=clock;
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private Parent owned(boolean group,UUID actor,UUID id,boolean lock) {
        var parent=data.parent(group,id,lock).orElseThrow(QuestionAuthoringException::missing);
        if(!access.any()&&!actor.equals(parent.owner()))throw QuestionAuthoringException.missing();
        return parent;
    }
    private void groupScope(UUID actor,UUID group) { if(group!=null)owned(true,actor,group,false); }
    private Parent question(UUID actor,UUID id) { var parent=owned(false,actor,id,false);groupScope(actor,parent.groupId());return parent; }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public QuestionAuthoringDtos.Page<QuestionAuthoringDtos.QuestionSummary> listQuestions(UUID actorId,QuestionAuthoringQuery query) {
        var page=data.list(false,actorId,access.any(),query);
        return new QuestionAuthoringDtos.Page<>(page.content().stream().map(data::questionSummary).toList(),page.page(),page.size(),page.totalElements(),page.totalPages());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public QuestionAuthoringDtos.Page<QuestionAuthoringDtos.GroupSummary> listGroups(UUID actorId,QuestionAuthoringQuery query) {
        var page=data.list(true,actorId,access.any(),query);
        return new QuestionAuthoringDtos.Page<>(page.content().stream().map(data::groupSummary).toList(),page.page(),page.size(),page.totalElements(),page.totalPages());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public QuestionAuthoringDtos.QuestionDetail getQuestion(UUID actorId,UUID id) { return questionView(actorId,question(actorId,id)); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public QuestionAuthoringDtos.GroupDetail getGroup(UUID actorId,UUID id) { return groupView(owned(true,actorId,id,false)); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<QuestionAuthoringDtos.Version> questionHistory(UUID actorId,UUID id) { return questionView(actorId,question(actorId,id)).versions(); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<QuestionAuthoringDtos.GroupVersion> groupHistory(UUID actorId,UUID id) { return groupView(owned(true,actorId,id,false)).versions(); }

    private QuestionAuthoringDtos.QuestionDetail questionView(UUID actor,Parent parent) {
        em.clear(); // JDBC writes and JPA snapshot reads share this transaction, without stale collections.
        var result=new ArrayList<QuestionAuthoringDtos.Version>();
        for(var version:questions.findAllForQuestion(parent.id())) {
            var pin=version.getGroupVersion();
            if(pin!=null) {
                if(!Objects.equals(parent.groupId(),pin.getGroup().getGroupId()))throw QuestionAuthoringException.conflict();
                groupScope(actor,pin.getGroup().getGroupId());
            } else if(parent.groupId()!=null)throw QuestionAuthoringException.conflict();
            var snapshot=mapper.from(version);
            result.add(new QuestionAuthoringDtos.Version(version.getQuestionVersionId(),version.getVersionNumber(),version.getStatus(),
                    snapshot.prompt().questionText(),snapshot.explanation(),version.getDifficulty(),pin==null?null:pin.getGroupVersionId(),
                    snapshot.prompt().passage(),snapshot.choices(),data.readableAssets(false,version.getQuestionVersionId(),access.readMedia()),
                    pin==null?List.of():data.readableAssets(true,pin.getGroupVersionId(),access.readMedia())));
        }
        return new QuestionAuthoringDtos.QuestionDetail(data.questionSummary(parent),result);
    }
    private QuestionAuthoringDtos.GroupDetail groupView(Parent parent) {
        em.clear();
        return new QuestionAuthoringDtos.GroupDetail(data.groupSummary(parent),groups.findAllVersions(parent.id()).stream().map(v->
                new QuestionAuthoringDtos.GroupVersion(v.getGroupVersionId(),v.getVersionNumber(),v.getStatus(),v.getInstruction(),v.getPassageText(),
                        data.readableAssets(true,v.getGroupVersionId(),access.readMedia()),v.getCreatedAt(),v.getPublishedAt(),v.getArchivedAt())).toList());
    }

    private Map<UUID,Parent> lockGroups(UUID actor,Collection<UUID> ids,boolean editable) {
        var sorted=new TreeSet<UUID>(Comparator.comparing(UUID::toString)); for(var id:ids)if(id!=null)sorted.add(id);
        var locked=new LinkedHashMap<UUID,Parent>();
        for(var id:sorted) { var parent=owned(true,actor,id,true); if(editable&&"ARCHIVED".equals(parent.status()))throw QuestionAuthoringException.conflict();locked.put(id,parent); }
        for(var id:sorted)data.versions(true,id,true);
        return locked;
    }
    private UUID groupOf(UUID groupVersion) {
        return groupVersion==null?null:data.version(true,groupVersion).orElseThrow(QuestionAuthoringException::missing).parent();
    }
    private Version groupPin(UUID actor,UUID versionId,UUID expectedGroup) {
        if(versionId==null) { if(expectedGroup!=null)throw QuestionAuthoringException.conflict();return null; }
        var version=data.version(true,versionId).orElseThrow(QuestionAuthoringException::missing);
        if(!version.parent().equals(expectedGroup))throw QuestionAuthoringException.conflict();
        var parent=owned(true,actor,expectedGroup,false);
        if("ARCHIVED".equals(parent.status()))throw QuestionAuthoringException.conflict();
        if(!"DRAFT".equals(version.status())&&!"PUBLISHED".equals(version.status()))throw QuestionAuthoringException.conflict();
        if("DRAFT".equals(version.status())&&(version.publishedAt()!=null||version.archivedAt()!=null))throw QuestionAuthoringException.conflict();
        if("PUBLISHED".equals(version.status()))validPublished(version);
        return version;
    }
    private Version historicalGroupPin(UUID versionId,UUID expectedGroup) {
        if(versionId==null) { if(expectedGroup!=null)throw QuestionAuthoringException.conflict();return null; }
        var version=data.version(true,versionId).orElseThrow(QuestionAuthoringException::conflict);
        if(!version.parent().equals(expectedGroup))throw QuestionAuthoringException.conflict();
        return version;
    }
    private void validPublished(Version version) {
        if(!"PUBLISHED".equals(version.status())||version.publishedAt()==null||version.publishedAt().isAfter(now())||version.archivedAt()!=null)
            throw QuestionAuthoringException.conflict();
    }
    private record Working(Version draft,Version source,boolean cloning,int nextNumber) {}
    private Working working(Parent parent,List<Version> versions) {
        if(!Set.of("DRAFT","PUBLISHED").contains(parent.status()))throw QuestionAuthoringException.conflict();
        Version published=null;
        if("PUBLISHED".equals(parent.status())) {
            if(parent.pointer()==null)throw QuestionAuthoringException.conflict();
            published=versions.stream().filter(v->v.id().equals(parent.pointer())).findFirst().orElseThrow(QuestionAuthoringException::conflict);
            validPublished(published);
        } else if(parent.pointer()!=null)throw QuestionAuthoringException.conflict();
        var drafts=versions.stream().filter(v->"DRAFT".equals(v.status())).toList();
        if(drafts.size()>1)throw QuestionAuthoringException.conflict();
        var draft=drafts.isEmpty()?null:drafts.getFirst();
        if(draft!=null&&(draft.publishedAt()!=null||draft.archivedAt()!=null))throw QuestionAuthoringException.conflict();
        if(draft==null&&published==null)throw QuestionAuthoringException.conflict();
        int maximum=versions.stream().mapToInt(Version::number).max().orElse(0);
        if(draft==null&&maximum==Integer.MAX_VALUE)throw QuestionAuthoringException.conflict();
        return new Working(draft,draft==null?published:draft,draft==null,maximum+1);
    }
    private static List<Link> links(QuestionAuthoringEdit edit) {
        if(edit.assets()==null)return List.of();
        var result=new ArrayList<Link>();var ids=new HashSet<UUID>();int index=0;
        for(var asset:edit.assets()) {
            if(!ids.add(asset.mediaId()))throw QuestionAuthoringException.invalid();
            result.add(new Link(asset.mediaId(),asset.transcript(),++index));
        }
        return List.copyOf(result);
    }
    private static List<Link> plus(List<Link> first,List<Link> second) { var result=new ArrayList<>(first);result.addAll(second);return result; }
    private List<Link> passageAssets(Version pin) { return pin==null?List.of():data.assets(true,pin.id()); }
    private void validateQuestion(int part,UUID group,String text,String difficulty,List<QuestionChoiceDraft> choices) {
        if(part<1||part>7||text==null||text.isBlank()||difficulty!=null&&!Set.of("EASY","MEDIUM","HARD").contains(difficulty))throw QuestionAuthoringException.invalid();
        if(Set.of(3,4,6,7).contains(part)!=(group!=null))throw QuestionAuthoringException.invalid();
        int count=part==2?3:4;
        if(choices==null||choices.size()!=count||choices.stream().anyMatch(Objects::isNull)||choices.stream().filter(QuestionChoiceDraft::correct).count()!=1)
            throw QuestionAuthoringException.invalid();
        var sorted=choices.stream().sorted(Comparator.comparingInt(QuestionChoiceDraft::displayOrder)).toList();
        for(int i=0;i<count;i++)if(sorted.get(i).displayOrder()!=i+1||!sorted.get(i).label().equals(String.valueOf((char)('A'+i))))
            throw QuestionAuthoringException.invalid();
    }
    private static void groupType(String type) { if(type!=null&&(type.isBlank()||type.length()>30))throw QuestionAuthoringException.invalid(); }
    private static void requireEdit(QuestionAuthoringEdit edit,boolean group) {
        var allowed=group?Set.of("groupType","instruction","passageText","assets")
                :Set.of("partId","topicId","questionTypeId","groupVersionId","questionText","explanation","difficulty","choices","assets");
        if(edit==null||edit.fields().isEmpty()||!allowed.containsAll(edit.fields())
                ||edit.has("assets")&&edit.assets()==null||edit.has("choices")&&edit.choices()==null
                ||edit.has("partId")&&edit.partId()==null||edit.has("questionTypeId")&&edit.questionTypeId()==null)
            throw QuestionAuthoringException.invalid();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public QuestionAuthoringDtos.GroupDetail createGroup(UUID actorId,QuestionAuthoringEdit edit) {
        requireEdit(edit,true);
        groupType(edit.groupType());var target=links(edit);media.validate(actorId,target,List.of(),false);
        var id=UUID.randomUUID();var time=now();data.insertGroup(id,actorId,edit.groupType(),time);
        var version=data.insertVersion(true,id,1,null,edit.instruction(),edit.passageText(),null,time);
        data.replaceAssets(true,version,target);
        return groupView(owned(true,actorId,id,false));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public QuestionAuthoringDtos.QuestionDetail createQuestion(UUID actorId,QuestionAuthoringEdit edit) {
        requireEdit(edit,false);
        if(!edit.has("partId")||!edit.has("questionText")||!edit.has("choices"))throw QuestionAuthoringException.invalid();
        int part=data.partNumber(edit.partId()),type=data.typeId(edit.questionTypeId());data.topic(edit.topicId());
        var gid=groupOf(edit.groupVersionId());
        lockGroups(actorId,Collections.singletonList(gid),true);
        var pin=groupPin(actorId,edit.groupVersionId(),gid);
        validateQuestion(part,gid,edit.questionText(),edit.difficulty(),edit.choices());
        var target=links(edit);
        media.validate(actorId,plus(target,passageAssets(pin)),passageAssets(pin),false);
        var id=UUID.randomUUID();var time=now();data.insertQuestion(id,actorId,edit.partId(),edit.topicId(),type,gid,time);
        var version=data.insertVersion(false,id,1,edit.groupVersionId(),edit.questionText(),edit.explanation(),edit.difficulty(),time);
        data.replaceChoices(version,edit.choices());data.replaceAssets(false,version,target);
        return questionView(actorId,question(actorId,id));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public QuestionAuthoringDtos.GroupDetail updateGroup(UUID actorId,UUID id,QuestionAuthoringEdit edit) {
        requireEdit(edit,true);
        var locked=lockGroups(actorId,List.of(id),true);var parent=locked.get(id);var all=data.versions(true,id,true);
        var working=working(parent,all);
        if(edit.has("groupType")&&(!"DRAFT".equals(parent.status())||all.size()!=1))throw QuestionAuthoringException.metadata();
        String type=edit.has("groupType")?edit.groupType():parent.groupType();groupType(type);
        var source=data.assets(true,working.source().id());var target=edit.has("assets")?links(edit):source;
        media.validate(actorId,target,source,working.cloning());
        var text=edit.has("instruction")?edit.instruction():working.source().text();
        var passage=edit.has("passageText")?edit.passageText():working.source().extra();
        var version=working.cloning()?data.insertVersion(true,id,working.nextNumber(),null,text,passage,null,now()):working.draft().id();
        if(!working.cloning())data.editVersion(true,version,null,text,passage,null);
        if(working.cloning()||edit.has("assets"))data.replaceAssets(true,version,target);
        data.updateGroup(parent,type,now());
        return groupView(owned(true,actorId,id,false));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public QuestionAuthoringDtos.QuestionDetail updateQuestion(UUID actorId,UUID id,QuestionAuthoringEdit edit) {
        requireEdit(edit,false);
        var observed=question(actorId,id);
        var proposedGroup=edit.has("groupVersionId")?groupOf(edit.groupVersionId()):observed.groupId();
        lockGroups(actorId,Arrays.asList(observed.groupId(),proposedGroup),true);
        var parent=owned(false,actorId,id,true);
        if(!Objects.equals(observed.groupId(),parent.groupId()))throw QuestionAuthoringException.conflict();
        var all=data.versions(false,id,true);var working=working(parent,all);var sourceVersion=working.source();
        boolean metadata=edit.has("partId")||edit.has("topicId")||edit.has("questionTypeId")||!Objects.equals(parent.groupId(),proposedGroup);
        if(metadata&&(!"DRAFT".equals(parent.status())||all.size()!=1))throw QuestionAuthoringException.metadata();
        Integer partId=edit.has("partId")?edit.partId():parent.partId();
        Integer topic=edit.has("topicId")?edit.topicId():parent.topicId();
        int type=data.typeId(edit.has("questionTypeId")?edit.questionTypeId():parent.typeId());data.topic(topic);
        var groupVersion=edit.has("groupVersionId")?edit.groupVersionId():sourceVersion.groupVersionId();
        var pin=groupPin(actorId,groupVersion,proposedGroup);
        // An explicit valid new pin may repair a question whose historical group version was archived.
        // The old snapshot is only read for clone/media checks; it is never reactivated or overwritten.
        var oldPin=historicalGroupPin(sourceVersion.groupVersionId(),parent.groupId());
        var text=edit.has("questionText")?edit.questionText():sourceVersion.text();
        var explanation=edit.has("explanation")?edit.explanation():sourceVersion.extra();
        var difficulty=edit.has("difficulty")?edit.difficulty():sourceVersion.difficulty();
        var choices=edit.has("choices")?edit.choices():data.choices(sourceVersion.id());
        validateQuestion(data.partNumber(partId),proposedGroup,text,difficulty,choices);
        var source=data.assets(false,sourceVersion.id());var target=edit.has("assets")?links(edit):source;
        media.validate(actorId,plus(target,passageAssets(pin)),plus(source,passageAssets(oldPin)),working.cloning());
        var version=working.cloning()?data.insertVersion(false,id,working.nextNumber(),groupVersion,text,explanation,difficulty,now()):working.draft().id();
        if(!working.cloning())data.editVersion(false,version,groupVersion,text,explanation,difficulty);
        if(working.cloning()||edit.has("choices"))data.replaceChoices(version,choices);
        if(working.cloning()||edit.has("assets"))data.replaceAssets(false,version,target);
        data.updateQuestion(parent,partId,topic,type,proposedGroup,now());
        return questionView(actorId,question(actorId,id));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void archiveGroup(UUID actorId,UUID id) { owned(true,actorId,id,true);data.archive(true,id,now()); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void archiveQuestion(UUID actorId,UUID id) {
        var observed=question(actorId,id);lockGroups(actorId,Collections.singletonList(observed.groupId()),false);
        var parent=owned(false,actorId,id,true);
        if(!Objects.equals(observed.groupId(),parent.groupId()))throw QuestionAuthoringException.conflict();
        data.archive(false,id,now());
    }
}
