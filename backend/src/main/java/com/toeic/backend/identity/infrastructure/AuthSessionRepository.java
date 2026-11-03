package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.domain.AuthSession;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @EntityGraph(attributePaths = {"user", "user.roles", "user.roles.permissions"})
    Optional<AuthSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now where s.sessionId = :id and s.revokedAt is null")
    int revoke(@Param("id") UUID sessionId, @Param("now") OffsetDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AuthSession s
            set s.revokedAt = case when s.createdAt > :now then s.createdAt else :now end
            where s.user.userId = :userId and s.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
