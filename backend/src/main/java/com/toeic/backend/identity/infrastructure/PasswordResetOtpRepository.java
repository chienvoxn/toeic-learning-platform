package com.toeic.backend.identity.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toeic.backend.identity.domain.PasswordResetOtp;

import jakarta.persistence.LockModeType;

public interface PasswordResetOtpRepository
        extends JpaRepository<PasswordResetOtp, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetOtp>
        findFirstByUser_UserIdOrderByCreatedAtDescOtpIdDesc(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update PasswordResetOtp o
            set o.usedAt = :now
            where o.user.userId = :userId
              and o.usedAt is null
            """)
    int invalidateUnused(
            @Param("userId") UUID userId,
            @Param("now") OffsetDateTime now
    );
}