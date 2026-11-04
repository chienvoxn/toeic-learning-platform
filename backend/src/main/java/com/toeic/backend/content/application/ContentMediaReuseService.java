package com.toeic.backend.content.application;

import com.toeic.backend.content.infrastructure.MediaReuseRepository;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Resource and grant locks are held until the enclosing authoring transaction commits. */
@Service
public class ContentMediaReuseService {
    private final MediaReuseRepository repository;
    private final ContentAccessPolicy access;
    public ContentMediaReuseService(MediaReuseRepository repository, ContentAccessPolicy access) {
        this.repository = repository;
        this.access = access;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasAnyAuthority('content:manage:self','content:manage:any') and @contentAccessPolicy.isActor(#actorId)")
    public void validate(UUID actorId, List<UUID> desired, List<UUID> source, boolean cloning) {
        var ids = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
        ids.addAll(desired);
        if (cloning) { ids.addAll(source); }
        var resources = new HashMap<UUID,MediaReuseRepository.Media>();
        for (var id : ids) { repository.lockMedia(id).ifPresent(media -> resources.put(id,media)); }
        var allowed = new HashMap<UUID,Boolean>();
        for (var id : ids) {
            var media = resources.get(id);
            boolean owner = media != null && actorId.equals(media.ownerId());
            boolean grant = !owner && repository.lockActiveGrant(id,actorId);
            boolean readable = media != null && (media.visibility().equals("PUBLIC")
                    || media.visibility().equals("AUTHENTICATED") && access.canReadMemberContent());
            allowed.put(id,readable && (owner || grant));
        }
        if (cloning && source.stream().anyMatch(id -> !allowed.getOrDefault(id,false))) {
            throw ContentException.mediaReuseConflict();
        }
        var retained = new HashSet<>(source);
        // Missing and forbidden newly supplied IDs use the same response; never include their metadata.
        if (desired.stream().anyMatch(id -> !allowed.getOrDefault(id,false) && !retained.contains(id))) {
            throw ContentException.mediaNotFound();
        }
        if (desired.stream().anyMatch(id -> !allowed.getOrDefault(id,false))) { throw ContentException.mediaReuseConflict(); }
    }
}
