package com.toeic.backend.identity.application;

import com.toeic.backend.identity.infrastructure.UserRepository;
import com.toeic.backend.identity.infrastructure.LearningProfileRepository;
import com.toeic.backend.identity.domain.LearningProfile;
import java.util.UUID;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class UserProfileService {
    private final UserRepository users;
    private final Clock clock;
    private final LearningProfileRepository learningProfiles;

    public UserProfileService(UserRepository users, Clock clock, LearningProfileRepository learningProfiles) {
        this.users = users;
        this.clock = clock;
        this.learningProfiles = learningProfiles;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('user:read:self') and @selfProfileAccess.isActor(#userId)")
    public UserProfile me(UUID userId) {
        var user = users.findByUserId(userId).orElseThrow(AuthException::invalidToken);
        if (!"ACTIVE".equals(user.getStatus())) {
            throw AuthException.invalidToken();
        }
        return UserProfile.from(user, learningProfiles.findByUserId(userId)
                .map(LearningProfile::getTargetScore).orElse(null));
    }

    @Transactional
    @PreAuthorize("hasAuthority('user:read:self') and hasAuthority('user:update:self') and @selfProfileAccess.isActor(#userId)")
    public UserProfile update(UUID userId, ProfileUpdate update) {
        if (update == null) { throw ProfileException.validation(); }
        var user = users.findForUpdate(userId).orElseThrow(AuthException::invalidToken);
        if (!"ACTIVE".equals(user.getStatus())) { throw AuthException.invalidToken(); }
        var now = OffsetDateTime.now(clock);
        if (update.fullNamePresent()) { user.updateFullName(update.fullName(), now); }
        var learningProfile = learningProfiles.findByUserId(userId);
        if (update.targetScorePresent()) {
            if (learningProfile.isPresent()) {
                learningProfile.get().updateTargetScore(update.targetScore(), now);
            } else if (update.targetScore() != null) {
                learningProfiles.save(new LearningProfile(userId, update.targetScore(), now));
            }
        }
        return UserProfile.from(user, update.targetScorePresent() ? update.targetScore()
                : learningProfile.map(LearningProfile::getTargetScore).orElse(null));
    }
}
