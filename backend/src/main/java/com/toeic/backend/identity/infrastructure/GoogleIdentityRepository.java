package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.domain.GoogleIdentity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleIdentityRepository extends JpaRepository<GoogleIdentity, String> {
    boolean existsByUserUserId(UUID userId);
}
