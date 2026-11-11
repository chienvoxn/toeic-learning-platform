package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.domain.LearningProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningProfileRepository extends JpaRepository<LearningProfile, UUID> {
    Optional<LearningProfile> findByUserId(UUID userId);
}
