package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.domain.EmailVerificationOtp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationOtpRepository
        extends JpaRepository<EmailVerificationOtp, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationOtp>
        findFirstByUser_UserIdOrderByCreatedAtDescOtpIdDesc(
                UUID userId
        );
}