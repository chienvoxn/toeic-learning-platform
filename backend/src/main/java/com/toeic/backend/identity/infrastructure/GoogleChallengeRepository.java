package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.domain.GoogleLoginChallenge;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GoogleChallengeRepository extends JpaRepository<GoogleLoginChallenge, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GoogleLoginChallenge c where c.id = :id")
    Optional<GoogleLoginChallenge> findLocked(@Param("id") UUID id);
    @Modifying
    @Query("delete from GoogleLoginChallenge c where c.expiresAt < :now")
    void deleteExpired(@Param("now") OffsetDateTime now);
}
