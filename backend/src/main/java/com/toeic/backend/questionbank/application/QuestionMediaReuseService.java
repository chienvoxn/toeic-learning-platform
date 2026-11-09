package com.toeic.backend.questionbank.application;

import com.toeic.backend.questionbank.infrastructure.QuestionAuthoringRepository;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class QuestionMediaReuseService {
    private final QuestionAuthoringRepository repository;
    private final QuestionAuthoringAccess access;
    public QuestionMediaReuseService(QuestionAuthoringRepository repository,QuestionAuthoringAccess access) { this.repository=repository;this.access=access; }

    @Transactional(propagation=Propagation.MANDATORY)
    @PreAuthorize("hasAnyAuthority('question:manage:self','question:manage:any') and @questionAuthoringAccess.isActor(#actorId)")
    public void validate(UUID actorId,List<QuestionAuthoringRepository.Link> desired,List<QuestionAuthoringRepository.Link> source,boolean cloning) {
        var ids=new TreeSet<UUID>(Comparator.comparing(UUID::toString));
        desired.forEach(a->ids.add(a.mediaId())); if(cloning)source.forEach(a->ids.add(a.mediaId()));
        var media=new HashMap<UUID,QuestionAuthoringRepository.Media>();
        for(var id:ids) repository.lockMedia(id).ifPresent(m->media.put(id,m));
        var allowed=new HashMap<UUID,Boolean>();
        // All media rows first, then grants in the same UUID order; locks last until commit.
        for(var id:ids) {
            var resource=media.get(id);
            boolean owner=resource!=null&&actorId.equals(resource.owner());
            boolean grant=!owner&&repository.lockGrant(id,actorId);
            boolean visible=resource!=null&&("PUBLIC".equals(resource.visibility())
                    ||"AUTHENTICATED".equals(resource.visibility())&&access.readMedia());
            allowed.put(id,visible&&Set.of("AUDIO","IMAGE").contains(resource.type())&&(owner||grant));
        }
        if(cloning&&source.stream().anyMatch(a->!allowed.getOrDefault(a.mediaId(),false)))throw QuestionAuthoringException.mediaConflict();
        var retained=new HashSet<UUID>();source.forEach(a->retained.add(a.mediaId()));
        if(desired.stream().anyMatch(a->!allowed.getOrDefault(a.mediaId(),false)&&!retained.contains(a.mediaId())))throw QuestionAuthoringException.mediaMissing();
        if(desired.stream().anyMatch(a->!allowed.getOrDefault(a.mediaId(),false)))throw QuestionAuthoringException.mediaConflict();
        if(desired.stream().anyMatch(a->a.transcript()!=null&&!"AUDIO".equals(media.get(a.mediaId()).type())))throw QuestionAuthoringException.invalid();
    }
}
